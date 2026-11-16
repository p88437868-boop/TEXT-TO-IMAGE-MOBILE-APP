package com.textpic.studio.domain

import com.textpic.license.QuotaRules
import com.textpic.studio.data.license.LicenseSnapshot

/**
 * The one place that decides whether one more image may be made, and that counts
 * it afterwards.
 *
 * A render counts when the user asks for a new image (Render in the keyboard,
 * Render & copy / Share / Save in the studio, and renders started from shared
 * text). The live preview, re-copying or re-sharing an image that already exists
 * in History, and failed renders do not count.
 */
class UsageGate(
    private val snapshot: () -> LicenseSnapshot,
    private val onRender: suspend () -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Null when a render is allowed; otherwise the sentence to show. */
    fun denialMessage(): String? {
        val current = runCatching(snapshot).getOrNull() ?: return null
        if (!current.licensed) return LicenseGate.ACTIVATE_MESSAGE
        if (!QuotaRules.canRender(current.quota, current.pendingUsage, clock())) {
            return QuotaRules.blockedMessage(current.quota, clock())
        }
        return null
    }

    fun canRender(): Boolean = denialMessage() == null

    /** "37 images left today" / "37 images left" / null for unlimited plans. */
    fun remainingLabel(): String? {
        val current = runCatching(snapshot).getOrNull() ?: return null
        if (!current.licensed) return null
        return QuotaRules.summary(current.quota, current.pendingUsage, clock())
    }

    fun isLow(): Boolean {
        val current = runCatching(snapshot).getOrNull() ?: return false
        return current.licensed && QuotaRules.isLow(current.quota, current.pendingUsage, clock())
    }

    /** Call after a successful render only. */
    suspend fun recordRender() {
        if (canRenderOrWasAllowed()) onRender()
    }

    private fun canRenderOrWasAllowed(): Boolean = runCatching(snapshot).getOrNull()?.licensed ?: false
}
