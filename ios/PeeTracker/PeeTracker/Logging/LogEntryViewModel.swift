import FirebaseAuth
import FirebaseFirestore
import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class LogEntryViewModel {
    private(set) var errorMessage: String?
    private(set) var isSaving = false

    func submitLog(groupId: String, startedAt: Date, durationSeconds: Int, volume: Volume, displayName: String) async {
        guard let uid = Auth.auth().currentUser?.uid else {
            errorMessage = "You must be signed in to log."
            return
        }

        isSaving = true
        errorMessage = nil

        let entry = LogEntry(
            uid: uid,
            displayName: displayName,
            timestamp: Timestamp(date: startedAt),
            durationSeconds: durationSeconds,
            volume: volume,
            dateKeyLocal: DateKeys.dateKeyLocal(for: startedAt),
            weekKeyLocal: DateKeys.weekKeyLocal(for: startedAt)
        )

        do {
            _ = try FirestorePaths.groupLogs(groupId: groupId).addDocument(from: entry)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } catch {
            errorMessage = error.localizedDescription
        }

        isSaving = false
    }
}
