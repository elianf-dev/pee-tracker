package com.peetracker.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Streak(
    val current: Long = 0,
    val longest: Long = 0,
    @get:PropertyName("lastLogDateKey") @set:PropertyName("lastLogDateKey")
    var lastLogDateKey: String = ""
)

data class UserProfile(
    @get:PropertyName("displayName") @set:PropertyName("displayName")
    var displayName: String = "",

    @get:PropertyName("photoURL") @set:PropertyName("photoURL")
    var photoURL: String? = null,

    @get:PropertyName("authProviders") @set:PropertyName("authProviders")
    var authProviders: List<String> = emptyList(),

    // Client-writable in Spark mode. GroupRepository's createGroup/joinGroup/leaveGroup update
    // this directly via arrayUnion/arrayRemove, batched with the membership write — there are no
    // callables and no Admin SDK to do it server-side, and the rules allow a user to write their
    // own doc. See SCHEMA.md's Spark Mode section.
    @get:PropertyName("currentGroupIds") @set:PropertyName("currentGroupIds")
    var currentGroupIds: List<String> = emptyList(),

    @get:PropertyName("timezone") @set:PropertyName("timezone")
    var timezone: String = "",

    @get:PropertyName("streak") @set:PropertyName("streak")
    var streak: Streak = Streak(),

    @get:PropertyName("fcmTokens") @set:PropertyName("fcmTokens")
    var fcmTokens: Map<String, String> = emptyMap(),

    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null
)
