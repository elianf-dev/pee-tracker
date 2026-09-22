import FirebaseFirestore
import Foundation
import Observation

enum LeaderboardPeriod: CaseIterable, Hashable {
    case daily
    case weekly

    var label: String {
        switch self {
        case .daily: "Today"
        case .weekly: "This Week"
        }
    }
}

@Observable
@MainActor
final class LeaderboardViewModel {
    let groupId: String
    let uid: String

    var selectedPeriod: LeaderboardPeriod = .daily
    private(set) var dailyEntries: [String: LeaderboardEntry] = [:]
    private(set) var weeklyEntries: [String: LeaderboardEntry] = [:]

    private var dailyListener: ListenerRegistration?
    private var weeklyListener: ListenerRegistration?

    var rows: [LeaderboardRow] {
        LeaderboardSort.rows(from: selectedPeriod == .daily ? dailyEntries : weeklyEntries)
    }

    init(groupId: String, uid: String) {
        self.groupId = groupId
        self.uid = uid
        start()
    }

    deinit {
        dailyListener?.remove()
        weeklyListener?.remove()
    }

    func stop() {
        dailyListener?.remove()
        dailyListener = nil
        weeklyListener?.remove()
        weeklyListener = nil
    }

    private func start() {
        let now = Date()
        // Reusing the exact key format LogEntryViewModel writes via DateKeys — a mismatch here
        // would point this listener at a leaderboard doc that never receives updates.
        let dateKey = DateKeys.dateKeyLocal(for: now)
        let weekKey = DateKeys.weekKeyLocal(for: now)

        dailyListener = FirestorePaths.leaderboardDaily(groupId: groupId, dateKey: dateKey)
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    guard let self else { return }
                    guard let snapshot, snapshot.exists else {
                        self.dailyEntries = [:]
                        return
                    }
                    self.dailyEntries = (try? snapshot.data(as: LeaderboardDocument.self))?.entries ?? [:]
                }
            }

        weeklyListener = FirestorePaths.leaderboardWeekly(groupId: groupId, weekKey: weekKey)
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    guard let self else { return }
                    guard let snapshot, snapshot.exists else {
                        self.weeklyEntries = [:]
                        return
                    }
                    self.weeklyEntries = (try? snapshot.data(as: LeaderboardDocument.self))?.entries ?? [:]
                }
            }
    }
}
