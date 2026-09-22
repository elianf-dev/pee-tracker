package com.peetracker.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class Volume(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromValue(value: String): Volume = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

data class LogEntry(
    @get:PropertyName("uid") @set:PropertyName("uid")
    var uid: String = "",

    @get:PropertyName("displayName") @set:PropertyName("displayName")
    var displayName: String = "",

    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Date? = null,

    @get:PropertyName("durationSeconds") @set:PropertyName("durationSeconds")
    var durationSeconds: Long = 0,

    @get:PropertyName("volume") @set:PropertyName("volume")
    var volume: String = Volume.MEDIUM.value,

    @get:PropertyName("dateKeyLocal") @set:PropertyName("dateKeyLocal")
    var dateKeyLocal: String = "",

    @get:PropertyName("weekKeyLocal") @set:PropertyName("weekKeyLocal")
    var weekKeyLocal: String = "",

    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null
)
