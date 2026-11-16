package com.textpic.studio.data.license

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.textpic.license.CachedLicense
import com.textpic.license.LicenseState
import com.textpic.license.QuotaInfo
import com.textpic.license.QuotaMode
import com.textpic.license.ServerReply
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** Everything this install remembers about its license. */
data class StoredLicense(
    val key: String? = null,
    val cached: CachedLicense = CachedLicense(key = null),
    val quota: QuotaInfo? = null,
    val pendingUsage: Int = 0,
    val pendingReportId: String? = null,
    val lastReportAt: Long? = null,
)

/**
 * Local storage for the activation key, the last signed license reply and the
 * few images not yet reported to the server.
 *
 * Neither the key nor the device code is ever written to a log.
 */
class LicenseStore(
    private val context: Context,
    storeName: String = DEFAULT_STORE_NAME,
) {
    private val store: DataStore<Preferences> by lazy { storeFor(context, storeName) }

    private object Keys {
        val licenseKey = stringPreferencesKey("license_key")
        val state = stringPreferencesKey("license_state")
        val name = stringPreferencesKey("licensee_name")
        val expiresAt = longPreferencesKey("expires_at")
        val activatedAt = longPreferencesKey("activated_at")
        val lastCheckedAt = longPreferencesKey("last_checked_at")
        val serverTime = longPreferencesKey("server_time")
        val maxWallClock = longPreferencesKey("max_wall_clock")
        val quotaMode = stringPreferencesKey("quota_mode")
        val quotaLimit = intPreferencesKey("quota_limit")
        val quotaUsedToday = intPreferencesKey("quota_used_today")
        val quotaUsedTotal = intPreferencesKey("quota_used_total")
        val quotaDayEndsAt = longPreferencesKey("quota_day_ends_at")
        val pendingUsage = intPreferencesKey("pending_usage")
        val pendingReportId = stringPreferencesKey("pending_report_id")
        val lastReportAt = longPreferencesKey("last_report_at")
        val fallbackDeviceId = stringPreferencesKey("fallback_device_id")
    }

    private suspend fun preferences(): Preferences? = runCatching {
        store.data
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyPreferences())
            }
            .first()
    }.getOrNull()

    suspend fun read(): StoredLicense {
        val prefs = preferences() ?: return StoredLicense()
        val key = prefs[Keys.licenseKey]?.takeIf { it.isNotBlank() }
        val quotaMode = prefs[Keys.quotaMode]
        val quota = if (quotaMode == null) {
            null
        } else {
            QuotaInfo(
                mode = QuotaMode.fromWire(quotaMode),
                limit = prefs[Keys.quotaLimit],
                usedToday = prefs[Keys.quotaUsedToday] ?: 0,
                usedTotal = prefs[Keys.quotaUsedTotal] ?: 0,
                remaining = null,
                dayEndsAt = prefs[Keys.quotaDayEndsAt]?.takeIf { it > 0 },
            )
        }
        val expiresAt = prefs[Keys.expiresAt]
        return StoredLicense(
            key = key,
            cached = CachedLicense(
                key = key,
                state = prefs[Keys.state]?.let(::parseState) ?: LicenseState.UNLICENSED,
                name = prefs[Keys.name].orEmpty(),
                expiresAt = expiresAt,
                activatedAt = prefs[Keys.activatedAt]?.takeIf { it > 0 },
                lastCheckedAt = prefs[Keys.lastCheckedAt]?.takeIf { it > 0 },
                serverTime = prefs[Keys.serverTime]?.takeIf { it > 0 },
                quota = quota,
                maxWallClock = prefs[Keys.maxWallClock] ?: 0L,
            ),
            quota = quota,
            pendingUsage = prefs[Keys.pendingUsage] ?: 0,
            pendingReportId = prefs[Keys.pendingReportId],
            lastReportAt = prefs[Keys.lastReportAt]?.takeIf { it > 0 },
        )
    }

    /**
     * Stores a verified server reply. [wallClock] is this phone's time when the
     * reply arrived and is used later to notice a backwards clock jump.
     */
    suspend fun saveReply(reply: ServerReply, wallClock: Long, pendingUsage: Int, reportedId: String?) {
        runCatching {
            store.edit { prefs ->
                prefs[Keys.state] = reply.state.name
                reply.name?.let { prefs[Keys.name] = it }
                val expiresAt = reply.expiresAt
                if (expiresAt != null) {
                    prefs[Keys.expiresAt] = expiresAt
                } else {
                    prefs.remove(Keys.expiresAt)
                }
                reply.activatedAt?.let { prefs[Keys.activatedAt] = it }
                prefs[Keys.lastCheckedAt] = wallClock
                prefs[Keys.serverTime] = reply.serverTime
                prefs[Keys.maxWallClock] = maxOf(prefs[Keys.maxWallClock] ?: 0L, wallClock, reply.serverTime)
                reply.quota?.let { quota ->
                    prefs[Keys.quotaMode] = quota.mode.name.lowercase()
                    val limit = quota.limit
                    if (limit != null) {
                        prefs[Keys.quotaLimit] = limit
                    } else {
                        prefs.remove(Keys.quotaLimit)
                    }
                    prefs[Keys.quotaUsedToday] = quota.usedToday
                    prefs[Keys.quotaUsedTotal] = quota.usedTotal
                    val dayEndsAt = quota.dayEndsAt
                    if (dayEndsAt != null) {
                        prefs[Keys.quotaDayEndsAt] = dayEndsAt
                    } else {
                        prefs.remove(Keys.quotaDayEndsAt)
                    }
                }
                val sentId = reportedId ?: prefs[Keys.pendingReportId]
                if (sentId != null && reply.reportedId == sentId) {
                    prefs[Keys.pendingUsage] = 0
                    prefs.remove(Keys.pendingReportId)
                }
            }
        }
    }

    suspend fun saveKey(key: String) {
        runCatching {
            store.edit { prefs ->
                prefs[Keys.licenseKey] = key
                prefs[Keys.state] = LicenseState.LICENSED.name
            }
        }
    }

    suspend fun recordWallClock(millis: Long) {
        runCatching {
            store.edit { prefs ->
                if (millis > (prefs[Keys.maxWallClock] ?: 0L)) prefs[Keys.maxWallClock] = millis
            }
        }
    }

    /** Adds one rendered image to the unreported count. */
    suspend fun addPendingRender(): Int {
        var result = 0
        runCatching {
            store.edit { prefs ->
                val pending = ((prefs[Keys.pendingUsage] ?: 0) + 1).coerceAtMost(10_000)
                prefs[Keys.pendingUsage] = pending
                result = pending
            }
        }
        return result
    }

    /** Remembers which report id is in flight so a retry is not counted twice. */
    suspend fun markReport(reportId: String, at: Long) {
        runCatching {
            store.edit { prefs ->
                prefs[Keys.pendingReportId] = reportId
                prefs[Keys.lastReportAt] = at
            }
        }
    }

    suspend fun clearPending() {
        runCatching {
            store.edit { prefs ->
                prefs[Keys.pendingUsage] = 0
                prefs.remove(Keys.pendingReportId)
            }
        }
    }

    /** Forgets the key entirely (Remove license). Usage totals stay on the server. */
    suspend fun clearAll() {
        runCatching {
            store.edit { prefs ->
                prefs.remove(Keys.licenseKey)
                prefs.remove(Keys.state)
                prefs.remove(Keys.name)
                prefs.remove(Keys.expiresAt)
                prefs.remove(Keys.activatedAt)
                prefs.remove(Keys.serverTime)
                prefs.remove(Keys.maxWallClock)
                prefs.remove(Keys.quotaMode)
                prefs.remove(Keys.quotaLimit)
                prefs.remove(Keys.quotaUsedToday)
                prefs.remove(Keys.quotaUsedTotal)
                prefs.remove(Keys.quotaDayEndsAt)
                prefs.remove(Keys.lastCheckedAt)
                prefs[Keys.pendingUsage] = 0
                prefs.remove(Keys.pendingReportId)
            }
        }
    }

    /** Only used when the system has no ANDROID_ID to read. */
    suspend fun fallbackDeviceId(create: () -> String): String {
        val existing = preferences()?.get(Keys.fallbackDeviceId)
        if (!existing.isNullOrBlank()) return existing
        val created = create()
        runCatching {
            store.edit { it[Keys.fallbackDeviceId] = created }
        }
        return created
    }

    private fun parseState(value: String): LicenseState =
        runCatching { LicenseState.valueOf(value) }.getOrDefault(LicenseState.UNLICENSED)

    companion object {
        const val DEFAULT_STORE_NAME = "textpic_license"
        private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

        private fun storeFor(context: Context, name: String): DataStore<Preferences> {
            val appContext = context.applicationContext
            return stores.getOrPut(name) {
                PreferenceDataStoreFactory.create(
                    produceFile = { File(appContext.filesDir, "datastore/$name.preferences_pb") },
                )
            }
        }
    }
}
