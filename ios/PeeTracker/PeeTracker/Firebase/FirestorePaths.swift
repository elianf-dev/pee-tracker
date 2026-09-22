import FirebaseFirestore

enum FirestorePaths {
    static func user(_ uid: String) -> DocumentReference {
        Firestore.firestore().collection("users").document(uid)
    }

    static func group(groupId: String) -> DocumentReference {
        Firestore.firestore().collection("groups").document(groupId)
    }

    static func groupMember(groupId: String, uid: String) -> DocumentReference {
        group(groupId: groupId).collection("members").document(uid)
    }

    static func groupLogs(groupId: String) -> CollectionReference {
        group(groupId: groupId).collection("logs")
    }

    static func groupLog(groupId: String, logId: String) -> DocumentReference {
        groupLogs(groupId: groupId).document(logId)
    }

    static func leaderboardDaily(groupId: String, dateKey: String) -> DocumentReference {
        group(groupId: groupId).collection("leaderboardDaily").document(dateKey)
    }

    static func leaderboardWeekly(groupId: String, weekKey: String) -> DocumentReference {
        group(groupId: groupId).collection("leaderboardWeekly").document(weekKey)
    }

    static func groupBadges(groupId: String) -> CollectionReference {
        group(groupId: groupId).collection("badges")
    }
}
