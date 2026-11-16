package com.textpic.license

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LicenseRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun cached(
        state: LicenseState = LicenseState.LICENSED,
        key: String? = "ABCD-EFGH-JKLM-NPQR",
        expiresAt: Long? = now + 30 * day,
        lastCheckedAt: Long? = now,
        maxWallClock: Long = now,
        serverTime: Long? = now,
    ) = CachedLicense(
        key = key,
        state = state,
        name = "Amina Khan",
        expiresAt = expiresAt,
        activatedAt = now - day,
        lastCheckedAt = lastCheckedAt,
        serverTime = serverTime,
        maxWallClock = maxWallClock,
    )

    @Test
    fun `no key means unlicensed and never crashes`() {
        val decision = LicenseRules.decide(null, now)
        assertThat(decision.licensed).isFalse()
        assertThat(decision.state).isEqualTo(LicenseState.UNLICENSED)
        assertThat(LicenseRules.decide(cached(key = ""), now).licensed).isFalse()
    }

    @Test
    fun `an active license works offline for seven days`() {
        assertThat(LicenseRules.decide(cached(lastCheckedAt = now - 6 * day), now).licensed).isTrue()
        val lastDay = LicenseRules.decide(cached(lastCheckedAt = now - 7 * day), now)
        assertThat(lastDay.licensed).isTrue()
        val tooOld = LicenseRules.decide(cached(lastCheckedAt = now - 8 * day), now)
        assertThat(tooOld.licensed).isFalse()
        assertThat(tooOld.state).isEqualTo(LicenseState.NEEDS_ONLINE)
        assertThat(tooOld.needsOnlineCheck).isTrue()
        assertThat(tooOld.message).isEqualTo(LicenseMessages.NEEDS_ONLINE)
    }

    @Test
    fun `blocked, cancelled and expired lock immediately`() {
        val blocked = LicenseRules.decide(cached(state = LicenseState.BLOCKED), now)
        assertThat(blocked.licensed).isFalse()
        assertThat(blocked.needsOnlineCheck).isFalse()
        assertThat(blocked.message).isEqualTo(LicenseMessages.BLOCKED)

        assertThat(LicenseRules.decide(cached(state = LicenseState.CANCELLED), now).message)
            .isEqualTo(LicenseMessages.CANCELLED)

        val expired = LicenseRules.decide(cached(expiresAt = now - 1), now)
        assertThat(expired.state).isEqualTo(LicenseState.EXPIRED)
        assertThat(expired.message).isEqualTo(LicenseMessages.EXPIRED)
    }

    @Test
    fun `a lifetime license never expires`() {
        val decision = LicenseRules.decide(cached(expiresAt = null), now)
        assertThat(decision.licensed).isTrue()
        assertThat(LicenseRules.daysLeft(null, now)).isNull()
    }

    @Test
    fun `a clock moved backwards requires an online check`() {
        val decision = LicenseRules.decide(
            cached(maxWallClock = now, lastCheckedAt = now - day),
            now - 3 * day,
        )
        assertThat(decision.licensed).isFalse()
        assertThat(decision.state).isEqualTo(LicenseState.CLOCK_CHANGED)
        assertThat(decision.needsOnlineCheck).isTrue()
    }

    @Test
    fun `normal clock drift is tolerated`() {
        val decision = LicenseRules.decide(
            cached(maxWallClock = now, lastCheckedAt = now - 1000),
            now - 60 * 60 * 1000,
        )
        assertThat(decision.licensed).isTrue()
    }

    @Test
    fun `days left rounds up and never goes negative`() {
        assertThat(LicenseRules.daysLeft(now + day, now)).isEqualTo(1)
        assertThat(LicenseRules.daysLeft(now + day + 1, now)).isEqualTo(2)
        assertThat(LicenseRules.daysLeft(now - 5 * day, now)).isEqualTo(0)
    }
}
