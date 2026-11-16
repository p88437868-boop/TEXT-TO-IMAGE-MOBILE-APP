package com.textpic.studio.domain

import com.textpic.license.LicenseMessages
import com.textpic.license.QuotaInfo
import com.textpic.license.QuotaRules

/**
 * Single place where "may this install produce output right now?" is decided.
 *
 * Rendering, inserting, copying, sharing, saving and template insertion all pass
 * through here, so hiding a button is never the only protection.
 */
class LicenseGate(
    private val licensedProvider: () -> Boolean,
    private val quotaProvider: () -> QuotaInfo? = { null },
    private val pendingProvider: () -> Int = { 0 },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Never throws: a broken provider counts as unlicensed. */
    fun isLicensed(): Boolean = runCatching { licensedProvider() }.getOrDefault(false)

    fun hasImagesLeft(): Boolean = runCatching {
        QuotaRules.canRender(quotaProvider(), pendingProvider(), clock())
    }.getOrDefault(true)

    /** Null when output is allowed, otherwise the sentence to show. */
    fun denialMessage(): String? {
        if (!isLicensed()) return ACTIVATE_MESSAGE
        if (!hasImagesLeft()) return QuotaRules.blockedMessage(quotaProvider(), clock())
        return null
    }

    companion object {
        val ACTIVATE_MESSAGE: String = LicenseMessages.ACTIVATE_TO_USE
    }
}
