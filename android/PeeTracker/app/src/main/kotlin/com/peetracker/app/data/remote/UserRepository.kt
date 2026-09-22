package com.peetracker.app.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.peetracker.app.data.model.UserProfile
import kotlinx.coroutines.tasks.await
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun fetchUserProfile(uid: String): UserProfile? {
        return firestore.document(FirestorePaths.userDoc(uid))
            .get()
            .await()
            .toObject(UserProfile::class.java)
    }

    // No onUserCreate trigger in Spark mode (see SCHEMA.md) — the client writes users/{uid}
    // itself. The exists() check makes this safe to call both right after a genuine new sign-up
    // and defensively on every session refresh, in case a prior attempt crashed between auth
    // succeeding and this write landing.
    suspend fun createProfileIfMissing(
        uid: String,
        displayName: String,
        photoURL: String?,
        authProviders: List<String>
    ) {
        val ref = firestore.document(FirestorePaths.userDoc(uid))
        if (ref.get().await().exists()) return
        ref.set(
            hashMapOf(
                "displayName" to displayName,
                "photoURL" to photoURL,
                "authProviders" to authProviders,
                "currentGroupIds" to emptyList<String>(),
                "timezone" to TimeZone.getDefault().id,
                "streak" to hashMapOf("current" to 0L, "longest" to 0L, "lastLogDateKey" to ""),
                "fcmTokens" to emptyMap<String, String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun resetStreakCurrent(uid: String, longest: Long, lastLogDateKey: String) {
        firestore.document(FirestorePaths.userDoc(uid))
            .update(
                "streak",
                hashMapOf("current" to 0L, "longest" to longest, "lastLogDateKey" to lastLogDateKey)
            )
            .await()
    }
}
