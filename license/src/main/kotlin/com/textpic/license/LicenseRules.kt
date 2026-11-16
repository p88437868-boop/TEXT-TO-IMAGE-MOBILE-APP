package com.textpic.license

/**
 * What this phone remembers from the last successful license check. Everything
 * here comes from a signed server reply (or from the stored key).
 */
data class CachedLicense(
    val key: String?,
    val state: LicenseState = LicenseState.UNLICENSED,
    val name: String = "",
    val expiresAt: Long? = null,
    val activatedAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val serverTime: Long? = null,
    val quota: QuotaInfo? = null,
    val maxWallClock: Long = 0L,
)

data class LicenseDecision(
    val state: LicenseState,
    val licensed: Boolean,
    val needsOnlineCheck: Boolean,
    val message: String?,
)

/**
 * Decides whether output features may run. Pure on purpose: the same rules run in
 * the app, in the keyboard and in the tests.
 *
 * Blocked, cancelled and expired lock immediately. A missing connection never
 * locks anyone by itself: the last signed reply stays good for [GRACE_DAYS], and
 * then the app asks for an online check instead of silently breaking.
 */
object LicenseRules {
    const val GRACE_DAYS = 7

    /** A clock jump smaller than this is ordinary drift, not tampering. */
    const val CLOCK_TOLERANCE_MS = 24L * 60 * 60 * 1000

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun decide(cached: CachedLicense?, now: Long): LicenseDecision {
        if (cached == null || cached.key.isNullOrBlank()) {
            return LicenseDecision(
                state = LicenseState.UNLICENSED,
                licensed = false,
                needsOnlineCheck = false,
                message = null,
            )
        }

        if (cached.maxWallClock > 0 && now < cached.maxWallClock - CLOCK_TOLERANCE_MS) {
            return locked(LicenseState.CLOCK_CHANGED, needsOnline = true)
        }

        when (cached.state) {
            LicenseState.BLOCKED -> return locked(LicenseState.BLOCKED, needsOnline = false)
            LicenseState.CANCELLED -> return locked(LicenseState.CANCELLED, needsOnline = false)
            LicenseState.WRONG_DEVICE -> return locked(LicenseState.WRONG_DEVICE, needsOnline = false)
            LicenseState.INVALID_KEY -> return locked(LicenseState.INVALID_KEY, needsOnline = false)
            else -> Unit
        }

        val expiresAt = cached.expiresAt
        if (expiresAt != null && now >= expiresAt) {
            return locked(LicenseState.EXPIRED, needsOnline = false)
        }

        val lastChecked = cached.lastCheckedAt
        if (lastChecked == null) {
            return locked(LicenseState.NEEDS_ONLINE, needsOnline = true)
        }

        // Use the newest of the two clocks we know about, so moving the phone
        // clock backwards cannot stretch the grace period.
        val reference = maxOf(now, cached.serverTime ?: 0L)
        if (reference - lastChecked > GRACE_DAYS * DAY_MS) {
            return locked(LicenseState.NEEDS_ONLINE, needsOnline = true)
        }

        return LicenseDecision(
            state = LicenseState.LICENSED,
            licensed = true,
            needsOnlineCheck = false,
            message = null,
        )
    }

    /** Days until the end date; null for a lifetime license. */
    fun daysLeft(expiresAt: Long?, now: Long): Long? {
        if (expiresAt == null) return null
        val remaining = expiresAt - now
        if (remaining <= 0) return 0
        return (remaining + DAY_MS - 1) / DAY_MS
    }

    private fun locked(state: LicenseState, needsOnline: Boolean) = LicenseDecision(
        state = state,
        licensed = false,
        needsOnlineCheck = needsOnline,
        message = LicenseMessages.forState(state),
    )
}
