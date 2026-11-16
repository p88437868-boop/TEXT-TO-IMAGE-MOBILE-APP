package com.textpic.license

/** How many images a license allows. */
enum class QuotaMode {
    UNLIMITED,
    DAILY,
    TOTAL,
    ;

    companion object {
        fun fromWire(value: String?): QuotaMode = when (value?.lowercase()) {
            "daily" -> DAILY
            "total" -> TOTAL
            else -> UNLIMITED
        }
    }
}

/**
 * The image allowance of a license, as confirmed by the server. [dayEndsAt] is
 * the next local midnight on the server's clock, so the phone can roll a daily
 * allowance forward while it is offline.
 */
data class QuotaInfo(
    val mode: QuotaMode,
    val limit: Int?,
    val usedToday: Int,
    val usedTotal: Int,
    val remaining: Int?,
    val dayEndsAt: Long?,
)

object QuotaRules {
    /** Unlimited plans have no count. */
    fun remainingNow(quota: QuotaInfo?, pending: Int, now: Long): Int? {
        if (quota == null) return null
        val allowance = allowanceNow(quota, now) ?: return null
        return (allowance - pending).coerceAtLeast(0)
    }

    /** Remaining images the server allows right now, before pending renders. */
    fun allowanceNow(quota: QuotaInfo, now: Long): Int? {
        val limit = quota.limit ?: return null
        return when (quota.mode) {
            QuotaMode.UNLIMITED -> null
            QuotaMode.TOTAL -> (limit - quota.usedTotal).coerceAtLeast(0)
            QuotaMode.DAILY -> {
                val freshDay = quota.dayEndsAt != null && now >= quota.dayEndsAt
                val used = if (freshDay) 0 else quota.usedToday
                (limit - used).coerceAtLeast(0)
            }
        }
    }

    fun canRender(quota: QuotaInfo?, pending: Int, now: Long): Boolean {
        val remaining = remainingNow(quota, pending, now) ?: return true
        return remaining > 0
    }

    fun blockedMessage(quota: QuotaInfo?, now: Long): String = when (quota?.mode) {
        QuotaMode.DAILY -> "Daily limit reached. Your images reset at midnight."
        QuotaMode.TOTAL -> "Image limit reached. Contact the owner to add more."
        else -> "Image limit reached. Contact the owner to add more."
    }

    /** `null` for unlimited plans; otherwise "37 images left today". */
    fun summary(quota: QuotaInfo?, pending: Int, now: Long): String? {
        if (quota == null) return null
        val remaining = remainingNow(quota, pending, now) ?: return null
        val unit = if (remaining == 1) "image" else "images"
        return when (quota.mode) {
            QuotaMode.DAILY -> "$remaining $unit left today"
            QuotaMode.TOTAL -> "$remaining $unit left"
            QuotaMode.UNLIMITED -> null
        }
    }

    /** True when 10% or less of the allowance is left. */
    fun isLow(quota: QuotaInfo?, pending: Int, now: Long): Boolean {
        if (quota == null) return false
        val limit = quota.limit ?: return false
        val remaining = remainingNow(quota, pending, now) ?: return false
        return limit > 0 && remaining <= (limit / 10).coerceAtLeast(1)
    }
}
