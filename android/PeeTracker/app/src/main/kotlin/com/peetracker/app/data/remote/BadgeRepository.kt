package com.peetracker.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.peetracker.app.data.model.Badge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgeRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // Read-only here: groups/{groupId}/badges is written directly by LogRepository (streak
    // milestones) and PeriodBadgeRepository (period badges) — see SCHEMA.md's Spark Mode
    // section. This listener itself never writes back.
    fun observeBadges(groupId: String): Flow<List<Badge>> = callbackFlow {
        val registration = firestore.collection(FirestorePaths.groupBadgesPath(groupId))
            .orderBy("awardedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Badge::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }
}
