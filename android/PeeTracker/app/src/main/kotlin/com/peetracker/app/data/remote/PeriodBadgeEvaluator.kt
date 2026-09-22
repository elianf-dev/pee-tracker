package com.peetracker.app.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.peetracker.app.data.model.LeaderboardEntry
import com.peetracker.app.util.runCatchingCancellable
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class BadgeAward(val type: String, val awardedToUid: String, val meta: Map<String, Any>)

// Ported from functions/src/lib/badges.ts's evaluateDailyBadges/evaluateWeeklyBadges. "Camel" =
// fewest trips logged (a camel can hold it), so this picks the minimum count, not the maximum.
object PeriodBadgeLogic {

    fun evaluateDaily(entries: Map<String, LeaderboardEntry>): List<BadgeAward> {
        val camel = entries.entries.filter { it.value.count > 0 }.minByOrNull { it.value.count }
            ?: return emptyList()
        return listOf(BadgeAward("camel_of_day", camel.key, mapOf("count" to camel.value.count)))
    }

    fun evaluateWeekly(
        weeklyEntries: Map<String, LeaderboardEntry>,
        dailyEntriesByDate: Map<String, Map<String, LeaderboardEntry>>
    ): List<BadgeAward> {
        val awards = mutableListOf<BadgeAward>()

        val camel = weeklyEntries.entries.filter { it.value.count > 0 }.minByOrNull { it.value.count }
        if (camel != null) {
            awards += BadgeAward("camel_of_week", camel.key, mapOf("count" to camel.value.count))
        }

        val dateKeys = dailyEntriesByDate.keys.toList()
        val candidateUids = weeklyEntries.keys.filter { uid ->
            dateKeys.all { dateKey -> (dailyEntriesByDate[dateKey]?.get(uid)?.count ?: 0) > 0 }
        }

        var mostRegularUid: String? = null
        var lowestStddev = Double.POSITIVE_INFINITY
        for (uid in candidateUids) {
            val dailyCounts = dateKeys.map { dateKey ->
                (dailyEntriesByDate[dateKey]?.get(uid)?.count ?: 0).toDouble()
            }
            val sd = stddev(dailyCounts)
            if (sd < lowestStddev) {
                lowestStddev = sd
                mostRegularUid = uid
            }
        }
        mostRegularUid?.let { uid ->
            awards += BadgeAward("most_regular", uid, mapOf("stddev" to lowestStddev))
        }

        return awards
    }

    private fun stddev(values: List<Double>): Double {
        if (values.isEmpty()) return Double.POSITIVE_INFINITY
        val mean = values.sum() / values.size
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}

// No server cron in Spark mode (see SCHEMA.md) — whichever device opens the Leaderboard screen
// after a day/week has rolled over computes and writes the previous period's badge itself. The
// badges/{badgeId} create rule uses a !exists() check, so a permission-denied failure here just
// means another device already wrote it first — that's the intended idempotent outcome, not an
// error.
@Singleton
class PeriodBadgeRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun finalizeYesterdayIfNeeded(groupId: String) {
        val dateKey = DateKeys.yesterdayDateKey()
        val entries = readLeaderboardEntries(FirestorePaths.groupLeaderboardDailyDoc(groupId, dateKey))
            ?: return
        awardBadges(groupId, dateKey, PeriodBadgeLogic.evaluateDaily(entries))
    }

    suspend fun finalizeLastWeekIfNeeded(groupId: String) {
        val weekKey = DateKeys.previousWeekKey()
        val weeklyEntries = readLeaderboardEntries(FirestorePaths.groupLeaderboardWeeklyDoc(groupId, weekKey))
            ?: return
        val dailyEntriesByDate = DateKeys.datesInIsoWeek(weekKey).associateWith { dateKey ->
            readLeaderboardEntries(FirestorePaths.groupLeaderboardDailyDoc(groupId, dateKey)) ?: emptyMap()
        }
        awardBadges(groupId, weekKey, PeriodBadgeLogic.evaluateWeekly(weeklyEntries, dailyEntriesByDate))
    }

    private suspend fun readLeaderboardEntries(path: String): Map<String, LeaderboardEntry>? {
        val snap = firestore.document(path).get().await()
        if (!snap.exists()) return null
        val raw = snap.get("entries") as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val fields = value as? Map<*, *> ?: return@mapNotNull null
            uid to LeaderboardEntry(
                count = (fields["count"] as? Number)?.toLong() ?: 0,
                totalDurationSeconds = (fields["totalDurationSeconds"] as? Number)?.toLong() ?: 0,
                displayName = fields["displayName"] as? String ?: ""
            )
        }.toMap()
    }

    private suspend fun awardBadges(groupId: String, periodKey: String, awards: List<BadgeAward>) {
        for (award in awards) {
            val badgeId = "${periodKey}_${award.type}"
            // A losing race on an already-awarded badge is expected and ignored, but cancellation
            // is not a failure to swallow: runCatching catches Throwable, so without rethrowing
            // CancellationException a cancelled scope would keep looping and firing further
            // writes after its owner is gone.
            runCatchingCancellable {
                firestore.document(FirestorePaths.groupBadgeDoc(groupId, badgeId))
                    .set(
                        hashMapOf(
                            "type" to award.type,
                            "periodKey" to periodKey,
                            "awardedToUid" to award.awardedToUid,
                            "awardedAt" to FieldValue.serverTimestamp(),
                            "meta" to award.meta
                        )
                    )
                    .await()
            }
        }
    }
}
