package com.textpic.license

/**
 * The local count of images rendered since the last confirmed report.
 *
 * The server is the authority on totals; this ledger only makes sure a render is
 * blocked immediately (before the next check) and that the images are reported
 * exactly once, even if the app is restarted or the network drops.
 */
object UsageLedger {
    const val MAX_PENDING = 10_000

    /** Debounce for the best-effort check after a render. */
    const val REPORT_INTERVAL_MS = 5L * 60 * 1000

    fun afterRender(pending: Int): Int = (pending + 1).coerceAtMost(MAX_PENDING)

    /** Clears the pending count only when the server confirmed our report id. */
    fun afterReply(pending: Int, sentReportId: String?, reportedId: String?): Int {
        if (sentReportId == null) return pending
        return if (reportedId == sentReportId) 0 else pending
    }

    fun shouldReport(lastReportAt: Long?, now: Long): Boolean =
        lastReportAt == null || now - lastReportAt >= REPORT_INTERVAL_MS
}
