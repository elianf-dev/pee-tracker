package com.peetracker.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Badge(
    @DocumentId
    var id: String? = null,

    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = "",

    @get:PropertyName("periodKey") @set:PropertyName("periodKey")
    var periodKey: String = "",

    @get:PropertyName("awardedToUid") @set:PropertyName("awardedToUid")
    var awardedToUid: String = "",

    @ServerTimestamp
    @get:PropertyName("awardedAt") @set:PropertyName("awardedAt")
    var awardedAt: Date? = null,

    // meta is badge-specific extra data (e.g. { count: 2 } or { streakCurrent: 7 }) that is
    // display-only per SCHEMA.md, so it is left as a loose Map rather than a typed model.
    @get:PropertyName("meta") @set:PropertyName("meta")
    var meta: Map<String, Any> = emptyMap()
)

enum class BadgeType(val id: String) {
    CAMEL_OF_DAY("camel_of_day"),
    CAMEL_OF_WEEK("camel_of_week"),
    MOST_REGULAR("most_regular"),
    STREAK_7("streak_7"),
    STREAK_30("streak_30");

    companion object {
        fun fromId(id: String): BadgeType? = entries.firstOrNull { it.id == id }
    }
}

data class BadgeCopy(
    val title: String,
    val emoji: String,
    val description: String
)

// Badge copy is intentionally not stored per-doc (see SCHEMA.md) — both native apps keep their
// own static type -> copy table so wording can be tuned without a data migration. The null
// branch is a defensive fallback for a badge type this build doesn't recognize yet.
fun badgeCopy(type: String): BadgeCopy = when (BadgeType.fromId(type)) {
    BadgeType.CAMEL_OF_DAY -> BadgeCopy("Camel of the Day", "🐫", "Fewest trips logged today")
    BadgeType.CAMEL_OF_WEEK -> BadgeCopy("Camel of the Week", "🐫", "Fewest trips logged this week")
    BadgeType.MOST_REGULAR -> BadgeCopy("Most Regular", "⏰", "Most consistent schedule this week")
    BadgeType.STREAK_7 -> BadgeCopy("Week Streak", "🔥", "Logged every day for 7 days straight")
    BadgeType.STREAK_30 -> BadgeCopy("Month Streak", "🏆", "Logged every day for 30 days straight")
    null -> BadgeCopy(type.replaceFirstChar { it.uppercase() }, "🎖️", "")
}
