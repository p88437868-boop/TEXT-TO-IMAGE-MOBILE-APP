package com.textpic.license

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuotaRulesTest {
    private val now = 1_800_000_000_000L
    private val midnight = now + 3_600_000

    private fun daily(limit: Int, used: Int) = QuotaInfo(
        mode = QuotaMode.DAILY,
        limit = limit,
        usedToday = used,
        usedTotal = used,
        remaining = limit - used,
        dayEndsAt = midnight,
    )

    private fun total(limit: Int, used: Int) = QuotaInfo(
        mode = QuotaMode.TOTAL,
        limit = limit,
        usedToday = used,
        usedTotal = used,
        remaining = limit - used,
        dayEndsAt = midnight,
    )

    private val unlimited = QuotaInfo(QuotaMode.UNLIMITED, null, 0, 0, null, midnight)

    @Test
    fun `unlimited plans never block`() {
        assertThat(QuotaRules.canRender(unlimited, pending = 9999, now = now)).isTrue()
        assertThat(QuotaRules.remainingNow(unlimited, 0, now)).isNull()
        assertThat(QuotaRules.summary(unlimited, 0, now)).isNull()
        assertThat(QuotaRules.isLow(unlimited, 0, now)).isFalse()
    }

    @Test
    fun `a daily plan counts pending renders`() {
        assertThat(QuotaRules.remainingNow(daily(50, 40), pending = 5, now = now)).isEqualTo(5)
        assertThat(QuotaRules.canRender(daily(50, 40), pending = 10, now = now)).isFalse()
        assertThat(QuotaRules.canRender(daily(50, 40), pending = 9, now = now)).isTrue()
        assertThat(QuotaRules.canRender(daily(50, 50), pending = 0, now = now)).isFalse()
    }

    @Test
    fun `a daily plan refills after midnight even while offline`() {
        val stale = daily(50, 50)
        assertThat(QuotaRules.canRender(stale, 0, now)).isFalse()
        val afterMidnight = QuotaRules.remainingNow(stale, 0, midnight + 1000)
        assertThat(afterMidnight).isEqualTo(50)
        assertThat(QuotaRules.canRender(stale, 0, midnight + 1000)).isTrue()
    }

    @Test
    fun `a whole period plan blocks at the total and ignores the day`() {
        assertThat(QuotaRules.canRender(total(1000, 999), 0, now)).isTrue()
        assertThat(QuotaRules.canRender(total(1000, 1000), 0, now)).isFalse()
        assertThat(QuotaRules.canRender(total(1000, 1000), 0, midnight + 1000)).isFalse()
        assertThat(QuotaRules.remainingNow(total(1000, 1200), 0, now)).isEqualTo(0)
    }

    @Test
    fun `messages match the plan`() {
        assertThat(QuotaRules.blockedMessage(daily(50, 50), now))
            .isEqualTo("Daily limit reached. Your images reset at midnight.")
        assertThat(QuotaRules.blockedMessage(total(10, 10), now))
            .isEqualTo("Image limit reached. Contact the owner to add more.")
    }

    @Test
    fun `summaries read like the app copy`() {
        assertThat(QuotaRules.summary(daily(50, 13), 0, now)).isEqualTo("37 images left today")
        assertThat(QuotaRules.summary(daily(2, 1), 0, now)).isEqualTo("1 image left today")
        assertThat(QuotaRules.summary(total(1000, 900), 0, now)).isEqualTo("100 images left")
    }

    @Test
    fun `a low allowance is flagged at ten percent`() {
        assertThat(QuotaRules.isLow(daily(50, 40), 0, now)).isFalse()
        assertThat(QuotaRules.isLow(daily(50, 45), 0, now)).isTrue()
        assertThat(QuotaRules.isLow(total(1000, 900), 0, now)).isTrue()
    }

    @Test
    fun `the local ledger keeps pending renders across a restart`() {
        assertThat(UsageLedger.afterRender(0)).isEqualTo(1)
        assertThat(UsageLedger.afterRender(UsageLedger.MAX_PENDING)).isEqualTo(UsageLedger.MAX_PENDING)
        assertThat(UsageLedger.afterReply(7, "r-1", "r-1")).isEqualTo(0)
        assertThat(UsageLedger.afterReply(7, "r-1", "r-2")).isEqualTo(7)
        assertThat(UsageLedger.afterReply(7, "r-1", null)).isEqualTo(7)
        assertThat(UsageLedger.afterReply(7, null, null)).isEqualTo(7)
    }

    @Test
    fun `reports are debounced to one every five minutes`() {
        assertThat(UsageLedger.shouldReport(null, now)).isTrue()
        assertThat(UsageLedger.shouldReport(now - 60_000, now)).isFalse()
        assertThat(UsageLedger.shouldReport(now - UsageLedger.REPORT_INTERVAL_MS, now)).isTrue()
    }
}
