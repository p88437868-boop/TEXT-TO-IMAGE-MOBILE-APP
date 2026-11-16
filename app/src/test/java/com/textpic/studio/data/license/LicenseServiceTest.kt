package com.textpic.studio.data.license

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.textpic.license.Json
import com.textpic.license.LicenseMessages
import com.textpic.license.LicenseState
import com.textpic.studio.TextPicApplication
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the whole activation path with a fake transport that signs replies
 * with a throwaway key pair, so signature checking, nonce checking, storage, the
 * offline grace window and the "never lock on a server error" rules are real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TextPicApplication::class)
class LicenseServiceTest {
    private lateinit var context: Context
    private lateinit var keys: KeyPair
    private lateinit var otherKeys: KeyPair
    private lateinit var transport: FakeTransport
    private lateinit var service: LicenseService

    private val now = 1_790_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val storeName = "test_license_" + UUID.randomUUID()
    private val device = "TP-5RKD-8WQZ-3MNB-6YTF"

    private inner class FakeTransport(
        var status: String = "ACTIVE",
        var name: String? = "Amina Khan",
        var expiresAt: Long? = now + 30 * day,
        var quota: String = UNLIMITED_QUOTA,
        var failure: Failure? = null,
        var signWith: KeyPair? = null,
        var corruptSignature: Boolean = false,
    ) : LicenseTransport {
        override val isConfigured: Boolean = true
        var lastBody: String? = null
        var calls = 0

        /** True when the transport was called on the UI thread (a crash on Android). */
        var calledOnMainThread = false

        override fun verify(body: String): LicenseApi.Result {
            lastBody = body
            calls++
            calledOnMainThread = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            when (failure) {
                Failure.UNREACHABLE -> return LicenseApi.Result.Unreachable()
                Failure.SERVER_ERROR -> return LicenseApi.Result.HttpError(500)
                Failure.RATE_LIMITED -> return LicenseApi.Result.HttpError(429)
                null -> Unit
            }
            val nonce = Json.parse(body)?.get("nonce")?.str() ?: error("no nonce")
            val payload = """{"status":"$status","name":${name?.let { "\"$it\"" } ?: "null"},""" +
                """"expiresAt":${expiresAt ?: "null"},"activatedAt":$now,"serverTime":$now,""" +
                """"nonce":"$nonce","quota":$quota,"reportedId":null}"""
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign((signWith ?: keys).private)
                update(payload.toByteArray())
                sign()
            }
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
            val sig = if (corruptSignature) encoded.reversed() else encoded
            return LicenseApi.Result.Ok(
                Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()),
                sig,
            )
        }
    }

    private enum class Failure { UNREACHABLE, SERVER_ERROR, RATE_LIMITED }

    private companion object {
        const val UNLIMITED_QUOTA =
            """{"mode":"unlimited","limit":null,"usedToday":0,"usedTotal":0,"remaining":null,"dayEndsAt":0}"""
        const val DAILY_QUOTA =
            """{"mode":"daily","limit":2,"usedToday":0,"usedTotal":0,"remaining":2,"dayEndsAt":0}"""
    }

    private fun newService(clockMillis: Long = now): LicenseService = LicenseService(
        context = context,
        api = transport,
        clock = { clockMillis },
        integrityOk = { true },
        publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
        storeName = storeName,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        otherKeys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        transport = FakeTransport()
        service = newService()
    }

    @Test
    fun `device code is stable and formatted`() = runBlocking {
        val first = service.deviceCode()
        assertThat(first).matches("TP-[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}")
        assertThat(newService().deviceCode()).isEqualTo(first)
    }

    @Test
    fun `a signed ACTIVE reply activates the phone and stores the key`() = runBlocking {
        val snapshot = service.refresh(online = true)
        assertThat(snapshot.state).isEqualTo(LicenseState.UNLICENSED)
        assertThat(snapshot.licensed).isFalse()

        val result = service.activate("abcd-efgh-jklm-npqr")
        assertThat(result).isInstanceOf(ActivationResult.Activated::class.java)
        assertThat((result as ActivationResult.Activated).name).isEqualTo("Amina Khan")

        val stored = LicenseStore(context, storeName).read()
        assertThat(stored.key).isEqualTo("abcd-efgh-jklm-npqr")
        assertThat(service.state.value.licensed).isTrue()
        assertThat(service.state.value.expiresAt).isEqualTo(now + 30 * day)

        // The request carries only the documented fields.
        val fields = Json.parse(transport.lastBody!!)!!.obj()!!.fields.keys
        assertThat(fields).containsExactly(
            "key",
            "deviceCode",
            "deviceModel",
            "androidVersion",
            "appVersion",
            "nonce",
            "usageCount",
        )
        Unit
    }

    @Test
    fun `a key for another phone is refused with the right sentence`() = runBlocking {
        transport.status = "WRONG_DEVICE"
        transport.name = null
        val result = service.activate("2345-6789-ABCD-EFGH")
        assertThat(result).isInstanceOf(ActivationResult.Rejected::class.java)
        assertThat((result as ActivationResult.Rejected).message)
            .isEqualTo(LicenseMessages.WRONG_DEVICE)
        assertThat(service.state.value.licensed).isFalse()
        assertThat(LicenseStore(context, storeName).read().key).isNull()
    }

    @Test
    fun `the server call happens off the main thread`() = runBlocking {
        // Android throws NetworkOnMainThreadException for sockets on the UI thread,
        // and that used to reach the user as "connect to the internet".
        service.activate("abcd-efgh-jklm-npqr")
        assertThat(transport.calls).isAtLeast(1)
        assertThat(transport.calledOnMainThread).isFalse()

        service.refresh(online = true)
        assertThat(transport.calledOnMainThread).isFalse()
    }

    @Test
    fun `an invalid key is refused`() = runBlocking {
        transport.status = "INVALID_KEY"
        val result = service.activate("2345-6789-ABCD-EFGH")
        assertThat((result as ActivationResult.Rejected).message)
            .isEqualTo(LicenseMessages.INVALID_KEY)
    }

    @Test
    fun `an unreachable server explains itself instead of blaming the internet`() = runBlocking {
        transport.failure = Failure.UNREACHABLE
        val result = service.activate("abcd-efgh-jklm-npqr")
        assertThat(result).isInstanceOf(ActivationResult.CannotReachServer::class.java)
        assertThat(service.state.value.message)
            .isEqualTo(LicenseMessages.SERVER_UNREACHABLE)
    }

    @Test
    fun `no internet means no activation and nothing stored`() = runBlocking {
        transport.failure = Failure.UNREACHABLE
        val result = service.activate("2345-6789-ABCD-EFGH")
        assertThat(result).isEqualTo(ActivationResult.CannotReachServer())
        assertThat(service.state.value.licensed).isFalse()
        assertThat(LicenseStore(context, storeName).read().key).isNull()
    }

    @Test
    fun `an unsigned or forged reply is rejected`() = runBlocking {
        transport.signWith = otherKeys
        val result = service.activate("2345-6789-ABCD-EFGH")
        assertThat(result).isInstanceOf(ActivationResult.Rejected::class.java)
        assertThat(service.state.value.licensed).isFalse()

        transport.signWith = null
        transport.corruptSignature = true
        val tampered = service.activate("2345-6789-ABCD-EFGH")
        assertThat(tampered).isInstanceOf(ActivationResult.Rejected::class.java)
        assertThat(LicenseStore(context, storeName).read().key).isNull()
    }

    @Test
    fun `an activated license keeps working offline for a week`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")
        transport.failure = Failure.UNREACHABLE

        val sixDays = newService(now + 6 * day).refresh(online = true)
        assertThat(sixDays.licensed).isTrue()

        val eightDays = newService(now + 8 * day).refresh(online = true)
        assertThat(eightDays.licensed).isFalse()
        assertThat(eightDays.state).isEqualTo(LicenseState.NEEDS_ONLINE)
        assertThat(eightDays.needsOnlineCheck).isTrue()
    }

    @Test
    fun `a server error never locks a paying customer`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")
        for (failure in listOf(Failure.SERVER_ERROR, Failure.RATE_LIMITED)) {
            transport.failure = failure
            val snapshot = newService(now + day).refresh(online = true)
            assertThat(snapshot.licensed).isTrue()
        }
    }

    @Test
    fun `a forged reply during a refresh never unlocks and never locks`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")
        transport.signWith = otherKeys
        val snapshot = newService(now + day).refresh(online = true)
        assertThat(snapshot.licensed).isTrue()
        assertThat(snapshot.message).isEqualTo(LicenseMessages.SERVER_ERROR)
    }

    @Test
    fun `block, cancel and expiry lock immediately`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")

        transport.status = "BLOCKED"
        val blocked = newService(now + 1000).refresh(online = true)
        assertThat(blocked.licensed).isFalse()
        assertThat(blocked.state).isEqualTo(LicenseState.BLOCKED)
        assertThat(blocked.message).isEqualTo(LicenseMessages.BLOCKED)

        transport.status = "CANCELLED"
        val cancelled = newService(now + 2000).refresh(online = true)
        assertThat(cancelled.state).isEqualTo(LicenseState.CANCELLED)

        transport.status = "ACTIVE"
        transport.expiresAt = now + 1000
        newService(now + 500).refresh(online = true)
        val expired = newService(now + 5000).refresh(online = true)
        assertThat(expired.state).isNotEqualTo(LicenseState.LICENSED)
    }

    @Test
    fun `the quota from the server reaches the gate`() = runBlocking {
        transport.quota = DAILY_QUOTA
        service.activate("2345-6789-ABCD-EFGH")
        val snapshot = service.state.value
        assertThat(snapshot.quota?.limit).isEqualTo(2)

        service.noteRender()
        service.noteRender()
        val after = service.state.value
        assertThat(after.pendingUsage).isEqualTo(2)
        assertThat(com.textpic.license.QuotaRules.canRender(after.quota, after.pendingUsage, now))
            .isFalse()
    }

    @Test
    fun `removing the license forgets the key`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")
        service.remove()
        assertThat(service.state.value.licensed).isFalse()
        assertThat(service.state.value.state).isEqualTo(LicenseState.UNLICENSED)
        assertThat(LicenseStore(context, storeName).read().key).isNull()
    }

    @Test
    fun `a closed clock jump is caught on the next decision`() = runBlocking {
        service.activate("2345-6789-ABCD-EFGH")
        transport.failure = Failure.UNREACHABLE
        val backwards = newService(now - 3 * day).refresh(online = true)
        assertThat(backwards.licensed).isFalse()
        assertThat(backwards.state).isEqualTo(LicenseState.CLOCK_CHANGED)
    }

    @Test
    fun `the device code in the stored snapshot matches the phone`() = runBlocking {
        val code = service.deviceCode()
        service.activate("2345-6789-ABCD-EFGH")
        assertThat(service.state.value.deviceCode).isEqualTo(code)
        assertThat(code).isNotEqualTo(device)
    }
}
