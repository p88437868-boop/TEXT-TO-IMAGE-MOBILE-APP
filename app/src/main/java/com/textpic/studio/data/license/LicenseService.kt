package com.textpic.studio.data.license

import android.content.Context
import android.os.Build
import com.textpic.license.CachedLicense
import com.textpic.license.LicenseApiContract
import com.textpic.license.LicenseDecision
import com.textpic.license.LicenseMessages
import com.textpic.license.LicenseRules
import com.textpic.license.LicenseState
import com.textpic.license.QuotaInfo
import com.textpic.license.ReplyVerification
import com.textpic.license.ServerReply
import com.textpic.license.SignedReplyVerifier
import com.textpic.license.UsageLedger
import com.textpic.studio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the UI reads. */
data class LicenseSnapshot(
    val loaded: Boolean = false,
    val state: LicenseState = LicenseState.UNKNOWN,
    val licensed: Boolean = false,
    val name: String = "",
    val key: String = "",
    val deviceCode: String = "",
    val expiresAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val quota: QuotaInfo? = null,
    val pendingUsage: Int = 0,
    val needsOnlineCheck: Boolean = false,
    val message: String? = null,
    val working: Boolean = false,
) {
    val isLicensed: Boolean get() = licensed

    fun daysLeft(now: Long = System.currentTimeMillis()): Long? =
        LicenseRules.daysLeft(expiresAt, now)
}

/** Result of tapping Activate. */
sealed interface ActivationResult {
    data class Activated(val name: String, val expiresAt: Long?) : ActivationResult
    data class Rejected(val message: String) : ActivationResult
    /** The request never got an answer; [detail] says why, when we know. */
    data class CannotReachServer(val detail: String = "") : ActivationResult
}

/**
 * Owns activation state for the whole process.
 *
 * The server is the authority. Every reply is checked against the public key
 * compiled into the app and against the nonce we sent, and the last good reply is
 * cached so the keyboard can answer instantly and keep working offline for a week.
 */
class LicenseService(
    context: Context,
    private val api: LicenseTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val integrityOk: () -> Boolean = { LicenseIntegrity.signatureMatches(context) },
    private val publicKeyBase64: String = com.textpic.studio.data.license.LicenseServerPublicKey.ENCODED,
    storeName: String = LicenseStore.DEFAULT_STORE_NAME,
) {
    private val store = LicenseStore(context, storeName)
    private val identity = DeviceIdentity(context, store)
    private val checkLock = Mutex()

    private val _state = MutableStateFlow(LicenseSnapshot())
    val state: StateFlow<LicenseSnapshot> = _state.asStateFlow()

    val serverConfigured: Boolean get() = api.isConfigured

    private var cachedDeviceCode: String? = null

    fun isLicensed(): Boolean = _state.value.licensed

    suspend fun deviceCode(): String {
        cachedDeviceCode?.let { return it }
        val code = runCatching { identity.codeText() }.getOrDefault("")
        cachedDeviceCode = code
        return code
    }

    /** True while an online check would be useful and allowed. */
    suspend fun shouldCheckNow(minIntervalMs: Long): Boolean {
        val stored = store.read()
        if (stored.key.isNullOrBlank()) return false
        val last = stored.cached.lastCheckedAt ?: return true
        return clock() - last >= minIntervalMs
    }

    /**
     * Reads the stored state and, when [online] is true, asks the server.
     * Never throws: any failure keeps the cached decision.
     */
    suspend fun refresh(online: Boolean = true): LicenseSnapshot {
        if (!checkLock.tryLock()) return _state.value
        try {
            val stored = store.read()
            val device = deviceCode()
            if (stored.key.isNullOrBlank()) {
                return publish(stored, LicenseState.UNLICENSED, emptyList = true, device = device)
            }
            if (!integrityOk()) {
                return publish(stored, LicenseState.INVALID_KEY, message = LicenseMessages.INVALID_KEY, device = device)
            }
            if (!online || !api.isConfigured) {
                return publishDecision(stored, device, message = null)
            }

            val nonce = LicenseApiContract.newNonce()
            val reportId = stored.pendingUsage.takeIf { it > 0 }?.let { LicenseApiContract.newReportId() }
            if (reportId != null) store.markReport(reportId, clock())
            val body = LicenseApiContract.buildRequestBody(
                key = stored.key,
                deviceCode = device,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
                androidVersion = Build.VERSION.RELEASE.orEmpty().take(32),
                appVersion = BuildConfig.VERSION_NAME.take(32),
                nonce = nonce,
                usageCount = stored.pendingUsage,
                reportId = reportId,
            )
            // The socket call must never run on the main thread: Android throws
            // NetworkOnMainThreadException, which used to look like "no internet".
            val result = withContext(Dispatchers.IO) { api.verify(body) }
            when (result) {
                is LicenseApi.Result.Ok -> {
                    when (
                        val verification = SignedReplyVerifier.verify(
                            payloadBase64Url = result.payload,
                            signatureBase64Url = result.sig,
                            publicKeyBase64 = publicKeyBase64,
                            expectedNonce = nonce,
                        )
                    ) {
                        is ReplyVerification.Verified -> {
                            val reply = verification.reply
                            store.saveReply(reply, clock(), stored.pendingUsage, reportId)
                            val updated = store.read()
                            return publishDecision(updated, device, message = null)
                        }
                        // A forged or broken reply must never lock a paying customer.
                        else -> return publishDecision(
                            stored,
                            device,
                            message = LicenseMessages.SERVER_ERROR,
                        )
                    }
                }
                is LicenseApi.Result.HttpError -> {
                    val message = if (result.code == 429) {
                        "Too many attempts. Wait a few minutes and try again."
                    } else {
                        LicenseMessages.SERVER_ERROR
                    }
                    if (stored.cached.state == LicenseState.UNLICENSED) {
                        return publish(stored, LicenseState.UNLICENSED, message = message, device = device)
                    }
                    return publishDecision(stored, device, message = message)
                }
                is LicenseApi.Result.Unreachable -> {
                    return publishDecision(
                        stored,
                        device,
                        message = LicenseMessages.unreachable(result.detail),
                    )
                }
            }
        } catch (error: Exception) {
            return _state.value
        } finally {
            checkLock.unlock()
        }
    }

    /**
     * Sends the key to the server, and stores it only when a signed reply says
     * this phone may use it.
     */
    suspend fun activate(rawKey: String): ActivationResult {
        if (!api.isConfigured) {
            return ActivationResult.Rejected(
                "This build has no license server address. Rebuild with textpic.licenseServerUrl set.",
            )
        }
        val key = rawKey.trim()
        if (key.isBlank()) return ActivationResult.Rejected(LicenseMessages.INVALID_KEY)
        val device = deviceCode()
        if (device.isBlank()) return ActivationResult.Rejected(LicenseMessages.SERVER_UNREACHABLE)
        if (!integrityOk()) return ActivationResult.Rejected(LicenseMessages.INVALID_KEY)

        _state.value = _state.value.copy(working = true)
        try {
            val nonce = LicenseApiContract.newNonce()
            val body = LicenseApiContract.buildRequestBody(
                key = key,
                deviceCode = device,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
                androidVersion = Build.VERSION.RELEASE.orEmpty().take(32),
                appVersion = BuildConfig.VERSION_NAME.take(32),
                nonce = nonce,
                usageCount = 0,
                reportId = null,
            )
            val result = withContext(Dispatchers.IO) { api.verify(body) }
            return when (result) {
                is LicenseApi.Result.Ok -> {
                    val verification = SignedReplyVerifier.verify(
                        result.payload,
                        result.sig,
                        publicKeyBase64,
                        nonce,
                    )
                    if (verification !is ReplyVerification.Verified) {
                        publishDecision(store.read(), device, LicenseMessages.SERVER_ERROR)
                        ActivationResult.Rejected(LicenseMessages.SERVER_ERROR)
                    } else {
                        when (verification.reply.state) {
                            LicenseState.LICENSED -> {
                                store.saveKey(key)
                                store.saveReply(verification.reply, clock(), 0, null)
                                publishDecision(store.read(), device, message = null)
                                ActivationResult.Activated(
                                    name = verification.reply.name.orEmpty(),
                                    expiresAt = verification.reply.expiresAt,
                                )
                            }
                            else -> {
                                // Keep the message, but do not store the key.
                                val reply = verification.reply
                                _state.value = _state.value.copy(
                                    loaded = true,
                                    working = false,
                                    state = reply.state,
                                    licensed = false,
                                    deviceCode = device,
                                    message = LicenseMessages.forState(reply.state),
                                    needsOnlineCheck = false,
                                )
                                ActivationResult.Rejected(
                                    LicenseMessages.forState(reply.state) ?: LicenseMessages.INVALID_KEY,
                                )
                            }
                        }
                    }
                }
                is LicenseApi.Result.HttpError -> {
                    _state.value = _state.value.copy(working = false)
                    if (result.code == 429) {
                        ActivationResult.Rejected("Too many attempts. Wait a few minutes and try again.")
                    } else {
                        ActivationResult.Rejected(LicenseMessages.SERVER_ERROR)
                    }
                }
                is LicenseApi.Result.Unreachable -> {
                    _state.value = _state.value.copy(
                        working = false,
                        message = LicenseMessages.unreachable(result.detail),
                    )
                    ActivationResult.CannotReachServer(result.detail)
                }
            }
        } finally {
            if (_state.value.working) _state.value = _state.value.copy(working = false)
        }
    }

    /** Counts one rendered image and reports it when the debounce allows. */
    suspend fun noteRender() {
        store.addPendingRender()
        val stored = store.read()
        _state.value = _state.value.copy(pendingUsage = stored.pendingUsage)
        if (UsageLedger.shouldReport(stored.lastReportAt, clock()) && api.isConfigured) {
            refresh(online = true)
        }
    }

    /** Force a report of pending images without touching the visible state. */
    suspend fun flushUsage() {
        val stored = store.read()
        if (stored.pendingUsage > 0 && api.isConfigured) refresh(online = true)
    }

    suspend fun remove() {
        store.clearAll()
        val device = runCatching { deviceCode() }.getOrDefault("")
        _state.value = LicenseSnapshot(
            loaded = true,
            state = LicenseState.UNLICENSED,
            licensed = false,
            deviceCode = device,
        )
    }

    private suspend fun publishDecision(
        stored: StoredLicense,
        device: String,
        message: String?,
    ): LicenseSnapshot {
        val decision: LicenseDecision = LicenseRules.decide(stored.cached, clock())
        if (decision.licensed) {
            runCatching { store.recordWallClock(clock()) }
        }
        val snapshot = LicenseSnapshot(
            loaded = true,
            state = decision.state,
            licensed = decision.licensed,
            name = stored.cached.name,
            key = stored.key.orEmpty(),
            deviceCode = device,
            expiresAt = stored.cached.expiresAt,
            lastCheckedAt = stored.cached.lastCheckedAt,
            quota = stored.cached.quota,
            pendingUsage = stored.pendingUsage,
            needsOnlineCheck = decision.needsOnlineCheck,
            message = message ?: decision.message,
        )
        _state.value = snapshot
        return snapshot
    }

    private fun publish(
        stored: StoredLicense,
        state: LicenseState,
        emptyList: Boolean = false,
        message: String? = null,
        device: String = "",
    ): LicenseSnapshot {
        val snapshot = LicenseSnapshot(
            loaded = true,
            state = state,
            licensed = state == LicenseState.LICENSED,
            name = if (emptyList) "" else stored.cached.name,
            key = stored.key.orEmpty(),
            deviceCode = device,
            expiresAt = stored.cached.expiresAt,
            lastCheckedAt = stored.cached.lastCheckedAt,
            quota = stored.cached.quota,
            pendingUsage = stored.pendingUsage,
            message = message,
        )
        _state.value = snapshot
        return snapshot
    }
}
