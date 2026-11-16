package com.textpic.studio.domain

import com.google.common.truth.Truth.assertThat
import com.textpic.license.QuotaInfo
import com.textpic.license.QuotaMode
import com.textpic.studio.data.license.LicenseSnapshot
import org.junit.Test

class LicenseGateTest {
    private val now = 1_790_000_000_000L

    @Test
    fun `gate refuses everything while unlicensed`() {
        val gate = LicenseGate(licensedProvider = { false })
        assertThat(gate.isLicensed()).isFalse()
        assertThat(gate.denialMessage()).isEqualTo(LicenseGate.ACTIVATE_MESSAGE)
    }

    @Test
    fun `gate allows features once licensed`() {
        val gate = LicenseGate(licensedProvider = { true })
        assertThat(gate.isLicensed()).isTrue()
        assertThat(gate.denialMessage()).isNull()
    }

    @Test
    fun `a broken provider counts as unlicensed instead of crashing`() {
        val gate = LicenseGate(licensedProvider = { error("boom") })
        assertThat(gate.isLicensed()).isFalse()
    }

    @Test
    fun `a used up daily plan blocks new images`() {
        val quota = QuotaInfo(QuotaMode.DAILY, 2, 2, 2, 0, now + 3_600_000)
        val gate = LicenseGate(
            licensedProvider = { true },
            quotaProvider = { quota },
            pendingProvider = { 0 },
            clock = { now },
        )
        assertThat(gate.hasImagesLeft()).isFalse()
        assertThat(gate.denialMessage())
            .isEqualTo("Daily limit reached. Your images reset at midnight.")
    }

    @Test
    fun `pending renders count before the server hears about them`() {
        val quota = QuotaInfo(QuotaMode.TOTAL, 10, 8, 8, 2, now)
        val gate = LicenseGate(
            licensedProvider = { true },
            quotaProvider = { quota },
            pendingProvider = { 2 },
            clock = { now },
        )
        assertThat(gate.denialMessage())
            .isEqualTo("Image limit reached. Contact the owner to add more.")
    }

    @Test
    fun `an unlimited plan never blocks and typing is never gated`() {
        val quota = QuotaInfo(QuotaMode.UNLIMITED, null, 500, 5000, null, now)
        val gate = LicenseGate(
            licensedProvider = { true },
            quotaProvider = { quota },
            pendingProvider = { 999 },
            clock = { now },
        )
        assertThat(gate.denialMessage()).isNull()
    }

    @Test
    fun `the usage gate mirrors the license gate`() {
        val licensed = LicenseSnapshot(loaded = true, licensed = true, state = com.textpic.license.LicenseState.LICENSED)
        val gate = UsageGate(snapshot = { licensed }, onRender = {}, clock = { now })
        assertThat(gate.canRender()).isTrue()
        assertThat(gate.remainingLabel()).isNull()

        val unlicensed = LicenseSnapshot(loaded = true, licensed = false)
        val blocked = UsageGate(snapshot = { unlicensed }, onRender = {}, clock = { now })
        assertThat(blocked.denialMessage()).isEqualTo(LicenseGate.ACTIVATE_MESSAGE)
    }

    @Test
    fun `the studio shows how many images are left`() {
        val quota = QuotaInfo(QuotaMode.DAILY, 50, 13, 13, 37, now + 1000)
        val snapshot = LicenseSnapshot(
            loaded = true,
            licensed = true,
            quota = quota,
            state = com.textpic.license.LicenseState.LICENSED,
        )
        val gate = UsageGate(snapshot = { snapshot }, onRender = {}, clock = { now })
        assertThat(gate.remainingLabel()).isEqualTo("37 images left today")
        assertThat(gate.isLow()).isFalse()
    }
}
