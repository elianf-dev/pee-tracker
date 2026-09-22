package com.peetracker.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.peetracker.app.data.model.Streak
import com.peetracker.app.data.model.UserProfile
import com.peetracker.app.data.model.Volume
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // One transaction for the log write, both leaderboard increments, the streak update, and
    // any streak-milestone badge: sequential writes could leave the log saved but the
    // leaderboard/streak silently out of sync if a later step failed (there's no server-side
    // onLogCreated trigger in Spark mode to guarantee that atomically instead).
    suspend fun submitLog(
        groupId: String,
        startTimestamp: Date,
        durationSeconds: Long,
        volume: Volume
    ) {
        val user = auth.currentUser ?: error("Cannot submit a log while signed out")
        val uid = user.uid
        val displayName = user.displayName.orEmpty()

        val dateKeyLocal = DateKeys.dateKey(startTimestamp)
        val weekKeyLocal = DateKeys.weekKey(startTimestamp)

        val logRef = firestore.collection(FirestorePaths.groupLogsPath(groupId)).document()
        val userRef = firestore.document(FirestorePaths.userDoc(uid))
        val dailyRef = firestore.document(FirestorePaths.groupLeaderboardDailyDoc(groupId, dateKeyLocal))
        val weeklyRef = firestore.document(FirestorePaths.groupLeaderboardWeeklyDoc(groupId, weekKeyLocal))

        firestore.runTransaction<Void?> { txn ->
            val userSnap = txn.get(userRef)
            val dailySnap = txn.get(dailyRef)
            val weeklySnap = txn.get(weeklyRef)

            txn.set(
                logRef,
                hashMapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "timestamp" to startTimestamp,
                    "durationSeconds" to durationSeconds,
                    "volume" to volume.value,
                    "dateKeyLocal" to dateKeyLocal,
                    "weekKeyLocal" to weekKeyLocal,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            applyLeaderboardDelta(txn, dailyRef, dailySnap, uid, displayName, durationSeconds)
            applyLeaderboardDelta(txn, weeklyRef, weeklySnap, uid, displayName, durationSeconds)

            val currentStreak = userSnap.toObject(UserProfile::class.java)?.streak ?: Streak()
            val newStreak = StreakLogic.computeUpdate(currentStreak, dateKeyLocal)
            if (newStreak != currentStreak) {
                txn.update(
                    userRef,
                    "streak",
                    hashMapOf(
                        "current" to newStreak.current,
                        "longest" to newStreak.longest,
                        "lastLogDateKey" to newStreak.lastLogDateKey
                    )
                )
            }

            StreakLogic.milestoneBadgeType(newStreak.current)?.let { badgeType ->
                val badgeRef = firestore.document(
                    FirestorePaths.groupBadgeDoc(groupId, "${uid}_$badgeType")
                )
                txn.set(
                    badgeRef,
                    hashMapOf(
                        "type" to badgeType,
                        "periodKey" to dateKeyLocal,
                        "awardedToUid" to uid,
                        "awardedAt" to FieldValue.serverTimestamp(),
                        "meta" to hashMapOf("streakCurrent" to newStreak.current)
                    )
                )
            }

            null
        }.await()
    }

    private fun applyLeaderboardDelta(
        txn: Transaction,
        ref: DocumentReference,
        snap: DocumentSnapshot,
        uid: String,
        displayName: String,
        durationSeconds: Long
    ) {
        if (!snap.exists()) {
            txn.set(
                ref,
                hashMapOf(
                    "entries" to hashMapOf(
                        uid to hashMapOf(
                            "count" to 1L,
                            "totalDurationSeconds" to durationSeconds,
                            "displayName" to displayName
                        )
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            return
        }

        val existing = (snap.get("entries") as? Map<*, *>)?.get(uid) as? Map<*, *>
        val existingCount = (existing?.get("count") as? Number)?.toLong() ?: 0L
        val existingDuration = (existing?.get("totalDurationSeconds") as? Number)?.toLong() ?: 0L
        txn.update(
            ref,
            mapOf(
                "entries.$uid" to hashMapOf(
                    "count" to existingCount + 1,
                    "totalDurationSeconds" to existingDuration + durationSeconds,
                    "displayName" to displayName
                ),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
    }
}
