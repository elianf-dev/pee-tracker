package com.peetracker.app.data.remote

import com.peetracker.app.data.model.Streak
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Ported from functions/src/lib/streaks.ts (computeStreakUpdate/streakMilestoneBadge/
// isStreakBroken), now run on-device since Spark mode has no onLogCreated trigger to do this
// server-side. dateKeyLocal strings are computed in each device's own timezone at write time
// (see SCHEMA.md), so comparing two keys as plain calendar dates is the same local-day
// approximation the original server code used, not an exact global-time comparison.
object StreakLogic {

    fun computeUpdate(current: Streak, newDateKey: String): Streak {
        if (newDateKey == current.lastLogDateKey) return current

        val isConsecutive = current.lastLogDateKey.isNotEmpty() &&
            daysBetween(current.lastLogDateKey, newDateKey) == 1L
        val newCurrent = if (isConsecutive) current.current + 1 else 1L

        return Streak(
            current = newCurrent,
            longest = maxOf(current.longest, newCurrent),
            lastLogDateKey = newDateKey
        )
    }

    fun milestoneBadgeType(streakCurrent: Long): String? = when (streakCurrent) {
        30L -> "streak_30"
        7L -> "streak_7"
        else -> null
    }

    fun isBroken(lastLogDateKey: String, todayDateKey: String): Boolean {
        if (lastLogDateKey.isEmpty()) return false
        return daysBetween(lastLogDateKey, todayDateKey) >= 2
    }

    private fun daysBetween(earlierDateKey: String, laterDateKey: String): Long =
        ChronoUnit.DAYS.between(LocalDate.parse(earlierDateKey), LocalDate.parse(laterDateKey))
}
