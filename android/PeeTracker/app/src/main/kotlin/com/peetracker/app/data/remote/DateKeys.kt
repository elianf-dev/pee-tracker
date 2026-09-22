package com.peetracker.app.data.remote

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date

// Shared by LogRepository (writes LogEntry.dateKeyLocal/weekKeyLocal) and LeaderboardRepository
// (reads groups/{groupId}/leaderboardDaily|Weekly/{key}). The server aggregates leaderboard docs
// keyed by these exact same strings, so this logic must never drift from either caller.
object DateKeys {

    fun dateKey(instant: Date, zone: ZoneId = ZoneId.systemDefault()): String {
        val localDate = LocalDate.ofInstant(instant.toInstant(), zone)
        return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun weekKey(instant: Date, zone: ZoneId = ZoneId.systemDefault()): String {
        val localDate = LocalDate.ofInstant(instant.toInstant(), zone)
        val weekFields = WeekFields.ISO
        val weekYear = localDate.get(weekFields.weekBasedYear())
        val weekOfYear = localDate.get(weekFields.weekOfWeekBasedYear())
        return "%04d-W%02d".format(weekYear, weekOfYear)
    }

    fun todayDateKey(): String = dateKey(Date())

    fun thisWeekKey(): String = weekKey(Date())

    fun yesterdayDateKey(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zone).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun previousWeekKey(zone: ZoneId = ZoneId.systemDefault()): String {
        val localDate = LocalDate.now(zone).minusWeeks(1)
        val weekFields = WeekFields.ISO
        val weekYear = localDate.get(weekFields.weekBasedYear())
        val weekOfYear = localDate.get(weekFields.weekOfWeekBasedYear())
        return "%04d-W%02d".format(weekYear, weekOfYear)
    }

    // Inverse of weekKey(): given "YYYY-Www", returns the 7 dateKeys (Mon..Sun) of that ISO
    // week, ported from dateKeys.ts's datesInIsoWeek so the weekly "most regular" badge can pull
    // each day's leaderboardDaily doc. Pure calendar math on the ISO year/week — no timezone
    // needed once we already have the week string.
    fun datesInIsoWeek(weekKey: String): List<String> {
        val match = Regex("^(\\d{4})-W(\\d{2})$").find(weekKey) ?: return emptyList()
        val isoYear = match.groupValues[1].toInt()
        val isoWeek = match.groupValues[2].toInt()
        val weekFields = WeekFields.ISO
        val monday = LocalDate.of(isoYear, 1, 4)
            .with(weekFields.weekOfWeekBasedYear(), isoWeek.toLong())
            .with(weekFields.dayOfWeek(), 1L)
        return (0 until 7).map { offset ->
            monday.plusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
}
