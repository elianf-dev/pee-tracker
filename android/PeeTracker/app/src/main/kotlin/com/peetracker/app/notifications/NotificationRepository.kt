package com.peetracker.app.notifications

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.peetracker.app.data.remote.FirestorePaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // Stable per-install id so a single account signed in on several devices gets a distinct
    // fcmTokens entry per device instead of the devices clobbering each other's token.
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            return UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        }

    suspend fun registerCurrentToken(uid: String) {
        val token = runCatching { messaging.token.await() }.getOrNull() ?: return
        writeToken(uid, token)
    }

    // Dot-path update() rather than a merge set() so only this device's entry in the fcmTokens
    // map is touched — a plain set(..., merge=true) with a nested map would replace the whole
    // map and drop every other device's token.
    suspend fun writeToken(uid: String, token: String) {
        firestore.document(FirestorePaths.userDoc(uid))
            .update(mapOf("fcmTokens.$deviceId" to token))
            .await()
    }

    suspend fun subscribeToGroup(groupId: String) {
        messaging.subscribeToTopic(topicFor(groupId)).await()
    }

    suspend fun unsubscribeFromGroup(groupId: String) {
        messaging.unsubscribeFromTopic(topicFor(groupId)).await()
    }

    private fun topicFor(groupId: String): String = "group_$groupId"

    private companion object {
        const val PREFS_NAME = "peetracker_notifications"
        const val KEY_DEVICE_ID = "device_id"
    }
}
