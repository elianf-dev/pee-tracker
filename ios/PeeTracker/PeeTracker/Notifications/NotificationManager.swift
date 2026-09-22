import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging
import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class NotificationManager {
    static let shared = NotificationManager()

    private static let deviceIdDefaultsKey = "com.peetracker.deviceId"

    private(set) lazy var deviceId: String = {
        if let existing = UserDefaults.standard.string(forKey: Self.deviceIdDefaultsKey) {
            return existing
        }
        let generated = UUID().uuidString
        UserDefaults.standard.set(generated, forKey: Self.deviceIdDefaultsKey)
        return generated
    }()

    private init() {}

    func requestAuthorizationIfNeeded() {
        Task {
            let center = UNUserNotificationCenter.current()
            let settings = await center.notificationSettings()
            guard settings.authorizationStatus == .notDetermined else { return }
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func didReceiveFCMToken(_ token: String?) {
        guard let token, let uid = Auth.auth().currentUser?.uid else { return }
        // fcmTokens is the one client-writable field on users/{uid} (firestore.rules only guards
        // `streak` and `currentGroupIds`); dot-path update touches just this device's entry so
        // other devices' tokens in the map are left alone.
        Task {
            try? await FirestorePaths.user(uid).updateData(["fcmTokens.\(deviceId)": token])
        }
    }

    func subscribe(to groupId: String) {
        Messaging.messaging().subscribe(toTopic: Self.topic(for: groupId)) { _ in }
    }

    func unsubscribe(from groupId: String) {
        Messaging.messaging().unsubscribe(fromTopic: Self.topic(for: groupId)) { _ in }
    }

    private static func topic(for groupId: String) -> String {
        "group_\(groupId)"
    }
}
