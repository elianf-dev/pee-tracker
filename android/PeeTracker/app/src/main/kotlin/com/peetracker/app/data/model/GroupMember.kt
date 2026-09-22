package com.peetracker.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class GroupMember(
    @get:PropertyName("displayName") @set:PropertyName("displayName")
    var displayName: String = "",

    @get:PropertyName("photoURL") @set:PropertyName("photoURL")
    var photoURL: String? = null,

    @ServerTimestamp
    @get:PropertyName("joinedAt") @set:PropertyName("joinedAt")
    var joinedAt: Date? = null,

    @get:PropertyName("role") @set:PropertyName("role")
    var role: String = "member"
)
