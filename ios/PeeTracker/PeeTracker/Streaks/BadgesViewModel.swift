import FirebaseFirestore
import Foundation
import Observation

@Observable
@MainActor
final class BadgesViewModel {
    let groupId: String
    let uid: String

    private(set) var badges: [Badge] = []
    var newlyAwardedBadge: Badge?

    private var listener: ListenerRegistration?
    private var seenBadgeIds: Set<String> = []
    private var isInitialLoad = true

    var myBadges: [Badge] {
        badges.filter { $0.awardedToUid == uid }
    }

    init(groupId: String, uid: String) {
        self.groupId = groupId
        self.uid = uid
        start()
    }

    deinit {
        listener?.remove()
    }

    func stop() {
        listener?.remove()
        listener = nil
    }

    func dismissCelebration() {
        newlyAwardedBadge = nil
    }

    private func start() {
        listener = FirestorePaths.groupBadges(groupId: groupId)
            .order(by: "awardedAt", descending: true)
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    guard let self, let snapshot else { return }
                    let decoded = snapshot.documents.compactMap { try? $0.data(as: Badge.self) }
                    self.badges = decoded

                    // The listener's first callback delivers every badge the group has ever
                    // earned — none of those are "new". Only badges arriving after this point
                    // (a genuinely fresh award) should trigger the celebration overlay.
                    guard !self.isInitialLoad else {
                        self.isInitialLoad = false
                        self.seenBadgeIds = Set(decoded.compactMap(\.id))
                        return
                    }

                    for badge in decoded where badge.awardedToUid == self.uid {
                        guard let id = badge.id, !self.seenBadgeIds.contains(id) else { continue }
                        self.seenBadgeIds.insert(id)
                        self.newlyAwardedBadge = badge
                    }
                }
            }
    }
}
