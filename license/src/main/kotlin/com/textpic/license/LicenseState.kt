package com.textpic.license

/** What the app currently believes about this install. */
enum class LicenseState {
    /** Nothing read yet (first frame). */
    UNKNOWN,

    /** Activated and allowed to work. */
    LICENSED,

    /** No key stored on this phone. */
    UNLICENSED,

    /** Blocked by the owner; locked immediately. */
    BLOCKED,

    /** Cancelled permanently by the owner; locked immediately. */
    CANCELLED,

    /** End date passed; locked immediately. */
    EXPIRED,

    /** The key belongs to another phone. */
    WRONG_DEVICE,

    /** The server does not know this key. */
    INVALID_KEY,

    /** The phone clock moved backwards; an online check is required. */
    CLOCK_CHANGED,

    /** The offline grace period is over; an online check is required. */
    NEEDS_ONLINE,
    ;

    val locksOutput: Boolean
        get() = this != LICENSED && this != UNKNOWN && this != UNLICENSED
}

/** Every sentence the activation screens may show. Kept together so they match. */
object LicenseMessages {
    const val INVALID_KEY = "This key is not valid."
    const val WRONG_DEVICE = "This key was made for a different phone."
    const val BLOCKED = "This license was blocked. Contact the owner."
    const val CANCELLED = "This license was cancelled."
    const val EXPIRED = "This license has expired."
    const val OFFLINE_ACTIVATE = "Connect to the internet to activate."
    const val NEEDS_ONLINE = "Connect to the internet to check your license."
    const val CLOCK_CHANGED =
        "Your phone's date is set backwards. Connect to the internet to check your license."
    const val SERVER_UNREACHABLE = "Can't reach the license server. Check your connection and try again."

    /** The same sentence with the real reason attached, never a guessed one. */
    fun unreachable(detail: String?): String =
        if (detail.isNullOrBlank()) SERVER_UNREACHABLE else "$SERVER_UNREACHABLE ($detail)"
    const val SERVER_ERROR = "The license server had a problem. Try again in a moment."
    const val ACTIVATE_TO_USE = "Activate TextPic to use this."

    fun forState(state: LicenseState): String? = when (state) {
        LicenseState.BLOCKED -> BLOCKED
        LicenseState.CANCELLED -> CANCELLED
        LicenseState.EXPIRED -> EXPIRED
        LicenseState.WRONG_DEVICE -> WRONG_DEVICE
        LicenseState.INVALID_KEY -> INVALID_KEY
        LicenseState.CLOCK_CHANGED -> CLOCK_CHANGED
        LicenseState.NEEDS_ONLINE -> NEEDS_ONLINE
        else -> null
    }
}
