package com.drawer.v2.domain

/**
 * In-memory counters for the current application process only. They are
 * deliberately not persisted: a new process starts a new sorting session.
 */
data class SessionSummary(
    val moved: Int = 0,
    val skipped: Int = 0,
    val keptCopies: Int = 0,
    val unreadable: Int = 0,
) {
    fun display(): String =
        "Session: $moved moved, $skipped skipped, $keptCopies kept copies, $unreadable unreadable."
}
