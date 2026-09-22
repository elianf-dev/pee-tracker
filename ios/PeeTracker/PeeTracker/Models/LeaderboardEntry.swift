import FirebaseFirestore

struct LeaderboardEntry: Codable {
    var count: Int
    var totalDurationSeconds: Int
    var displayName: String
}

// Mirrors groups/{groupId}/leaderboardDaily|leaderboardWeekly/{key} — server-managed only
// (onLogCreated/onLogDeleted, dailyReset/weeklyReset); this app only ever decodes it.
struct LeaderboardDocument: Codable {
    var entries: [String: LeaderboardEntry]
    var updatedAt: Timestamp?
    var finalized: Bool?
    var finalizedAt: Timestamp?
}

struct LeaderboardRow: Identifiable {
    let uid: String
    let entry: LeaderboardEntry

    var id: String { uid }
}

enum LeaderboardSort {
    static func rows(from entries: [String: LeaderboardEntry]) -> [LeaderboardRow] {
        entries
            .map { LeaderboardRow(uid: $0.key, entry: $0.value) }
            .sorted { lhs, rhs in
                if lhs.entry.count != rhs.entry.count {
                    return lhs.entry.count > rhs.entry.count
                }
                return lhs.entry.displayName < rhs.entry.displayName
            }
    }
}
