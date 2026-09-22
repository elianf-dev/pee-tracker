package com.peetracker.app.data.remote

object FirestorePaths {

    fun userDoc(uid: String): String = "users/$uid"

    fun groupPath(groupId: String): String = "groups/$groupId"

    fun groupMemberPath(groupId: String, uid: String): String = "groups/$groupId/members/$uid"

    fun groupLogsPath(groupId: String): String = "groups/$groupId/logs"

    fun groupLeaderboardDailyDoc(groupId: String, dateKey: String): String =
        "groups/$groupId/leaderboardDaily/$dateKey"

    fun groupLeaderboardWeeklyDoc(groupId: String, weekKey: String): String =
        "groups/$groupId/leaderboardWeekly/$weekKey"

    fun groupBadgesPath(groupId: String): String = "groups/$groupId/badges"

    fun groupBadgeDoc(groupId: String, badgeId: String): String = "groups/$groupId/badges/$badgeId"
}
