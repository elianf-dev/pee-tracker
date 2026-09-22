package com.peetracker.app.data.model

import com.google.firebase.firestore.PropertyName

data class LeaderboardEntry(
    @get:PropertyName("count") @set:PropertyName("count")
    var count: Long = 0,

    @get:PropertyName("totalDurationSeconds") @set:PropertyName("totalDurationSeconds")
    var totalDurationSeconds: Long = 0,

    @get:PropertyName("displayName") @set:PropertyName("displayName")
    var displayName: String = ""
)
