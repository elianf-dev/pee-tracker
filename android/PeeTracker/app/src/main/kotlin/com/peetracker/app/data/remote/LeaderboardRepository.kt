package com.peetracker.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.peetracker.app.data.model.LeaderboardEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaderboardRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun observeDailyLeaderboard(groupId: String): Flow<Map<String, LeaderboardEntry>> =
        observe(FirestorePaths.groupLeaderboardDailyDoc(groupId, DateKeys.todayDateKey()))

    fun observeWeeklyLeaderboard(groupId: String): Flow<Map<String, LeaderboardEntry>> =
        observe(FirestorePaths.groupLeaderboardWeeklyDoc(groupId, DateKeys.thisWeekKey()))

    // Read-only here: leaderboardDaily/leaderboardWeekly docs are written directly by
    // LogRepository (per-log increments) and PeriodBadgeRepository (finalize checks) — see
    // SCHEMA.md's Spark Mode section. This listener itself never writes back.
    private fun observe(path: String): Flow<Map<String, LeaderboardEntry>> = callbackFlow {
        val registration = firestore.document(path).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(parseEntries(snapshot?.data))
        }
        awaitClose { registration.remove() }
    }

    private fun parseEntries(data: Map<String, Any?>?): Map<String, LeaderboardEntry> {
        val raw = data?.get("entries") as? Map<*, *> ?: return emptyMap()
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
}
