package com.peetracker.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class GroupSettings(
    @get:PropertyName("dailyResetHour") @set:PropertyName("dailyResetHour")
    var dailyResetHour: Long = 0,

    @get:PropertyName("weeklyResetWeekday") @set:PropertyName("weeklyResetWeekday")
    var weeklyResetWeekday: Long = 0
)

data class Group(
    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("inviteCode") @set:PropertyName("inviteCode")
    var inviteCode: String = "",

    @get:PropertyName("ownerUid") @set:PropertyName("ownerUid")
    var ownerUid: String = "",

    @get:PropertyName("memberCount") @set:PropertyName("memberCount")
    var memberCount: Long = 0,

    @get:PropertyName("settings") @set:PropertyName("settings")
    var settings: GroupSettings = GroupSettings(),

    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null
)
