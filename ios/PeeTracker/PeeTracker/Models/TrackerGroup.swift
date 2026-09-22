import FirebaseFirestore

// Named `TrackerGroup` rather than `Group` to avoid shadowing SwiftUI.Group in any file
// that imports both this module and SwiftUI.
struct TrackerGroup: Codable {
    struct GroupSettings: Codable {
        var dailyResetHour: Int
        var weeklyResetWeekday: Int
    }

    var name: String
    var inviteCode: String
    var ownerUid: String
    var memberCount: Int
    var settings: GroupSettings
    var createdAt: Timestamp
}
