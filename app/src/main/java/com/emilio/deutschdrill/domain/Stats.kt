package com.emilio.deutschdrill.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionSpan(val startedAt: Long, val durationMs: Long)

data class DailyStats(
    val streakDays: Int,
    val practicedToday: Boolean,
    val minutesToday: Double,
    /** Minutes in the current block of back-to-back sessions (for the fatigue cap). */
    val currentBlockMinutes: Double,
)

object Stats {
    /** A day counts for the streak if it has at least one session of 2+ minutes. */
    const val STREAK_MIN_SESSION_MS = 2 * 60_000L
    /** Sessions separated by less than this are one continuous block. */
    const val BLOCK_GAP_MS = 5 * 60_000L
    const val BLOCK_CAP_MINUTES = 12.0

    fun compute(sessions: List<SessionSpan>, now: Long, zone: ZoneId): DailyStats {
        val today = day(now, zone)
        val qualifying = sessions.filter { it.durationMs >= STREAK_MIN_SESSION_MS }.map { day(it.startedAt, zone) }.toSet()
        val practicedToday = today in qualifying
        var cursor: LocalDate = if (practicedToday) today else today.minusDays(1)
        var streak = 0
        while (cursor in qualifying) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val minutesToday = sessions.filter { day(it.startedAt, zone) == today }.sumOf { it.durationMs } / 60_000.0

        var block = 0L
        var blockEnd = now
        for (s in sessions.sortedByDescending { it.startedAt }) {
            val end = s.startedAt + s.durationMs
            if (blockEnd - end > BLOCK_GAP_MS) break
            block += s.durationMs
            blockEnd = s.startedAt
        }
        return DailyStats(streak, practicedToday, minutesToday, block / 60_000.0)
    }

    private fun day(ts: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
}
