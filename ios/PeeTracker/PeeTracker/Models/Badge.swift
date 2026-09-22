import FirebaseFirestore

enum BadgeType: String, Codable, CaseIterable {
    case camelOfDay = "camel_of_day"
    case camelOfWeek = "camel_of_week"
    case mostRegular = "most_regular"
    case streak7 = "streak_7"
    case streak30 = "streak_30"

    var copy: BadgeCopy {
        switch self {
        case .camelOfDay:
            BadgeCopy(title: "Camel of the Day", emoji: "🐫")
        case .camelOfWeek:
            BadgeCopy(title: "Camel of the Week", emoji: "🐫")
        case .mostRegular:
            BadgeCopy(title: "Most Regular", emoji: "⏰")
        case .streak7:
            BadgeCopy(title: "Week Streak", emoji: "🔥")
        case .streak30:
            BadgeCopy(title: "Month Streak", emoji: "🏆")
        }
    }
}

struct BadgeCopy {
    let title: String
    let emoji: String
}

// Mirrors groups/{groupId}/badges/{badgeId} — server-managed and read-only from the client.
// `meta` is intentionally not decoded: SCHEMA.md documents it as display-only with a shape that
// varies per badge type (numbers for some types, strings for others), so typing it here would
// just be a source of decode failures for a field nothing in this app reads.
struct Badge: Codable, Identifiable {
    @DocumentID var id: String?
    var type: BadgeType
    var periodKey: String
    var awardedToUid: String
    var awardedAt: Timestamp?
}
