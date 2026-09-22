import FirebaseFirestore

struct UserProfile: Codable {
    struct Streak: Codable {
        var current: Int
        var longest: Int
        var lastLogDateKey: String
    }

    var displayName: String
    var photoURL: String?
    var authProviders: [String]
    var currentGroupIds: [String]
    var timezone: String
    // Server-managed (onUserCreate / future Phase 4 updates); firestore.rules rejects
    // any client write that changes this field, so this app only ever reads it.
    var streak: Streak
    var createdAt: Timestamp
}
