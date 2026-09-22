import Foundation

// Shared by LogEntryViewModel (writing logs) and LeaderboardViewModel (listening on the matching
// leaderboardDaily/leaderboardWeekly docs) — both must derive the exact same key for the exact
// same instant, or the leaderboard listener silently points at a doc no log ever lands in.
enum DateKeys {
    nonisolated static func dateKeyLocal(for date: Date, timeZone: TimeZone = .current) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        guard let year = components.year, let month = components.month, let day = components.day else {
            return ""
        }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    nonisolated static func weekKeyLocal(for date: Date, timeZone: TimeZone = .current) -> String {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = timeZone
        let components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        guard let year = components.yearForWeekOfYear, let week = components.weekOfYear else {
            return ""
        }
        return String(format: "%04d-W%02d", year, week)
    }
}
