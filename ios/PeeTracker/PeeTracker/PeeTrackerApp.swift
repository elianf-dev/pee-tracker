import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import SwiftUI
import UIKit
import UserNotifications

@main
struct PeeTrackerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var appState = AppState()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                    if let code = Self.inviteCode(from: url) {
                        appState.pendingInviteCode = code
                    }
                }
        }
    }

    private static func inviteCode(from url: URL) -> String? {
        guard url.scheme == "peetracker", url.host == "join" else { return nil }
        return url.pathComponents.first { $0 != "/" }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        NotificationManager.shared.didReceiveFCMToken(fcmToken)
    }

    // Without this, APNs delivers silently while the app is foregrounded and nothing is shown.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

private struct RootView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        content
            .sheet(isPresented: joinSheetBinding) {
                JoinGroupView(prefilledCode: appState.pendingInviteCode ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch appState.signInState {
        case .signedOut:
            AuthView()
        case .signedIn(let uid):
            if let currentUser = appState.currentUser {
                if currentUser.currentGroupIds.isEmpty {
                    CreateOrJoinGroupView()
                } else if let groupId = appState.activeGroupId {
                    TabView {
                        LogEntryView()
                            .tabItem {
                                Label("Log", systemImage: "drop.fill")
                            }
                        LeaderboardView(groupId: groupId, uid: uid)
                            .tabItem {
                                Label("Leaderboard", systemImage: "trophy.fill")
                            }
                        StreakView(groupId: groupId, uid: uid)
                            .tabItem {
                                Label("Streaks", systemImage: "flame.fill")
                            }
                    }
                    // Requested here rather than during onboarding: the user already has a
                    // concrete reason ("this group can now notify you") instead of a cold prompt
                    // before they've done anything.
                    .onAppear {
                        NotificationManager.shared.requestAuthorizationIfNeeded()
                    }
                } else {
                    ProgressView()
                }
            } else {
                ProgressView()
            }
        }
    }

    private var joinSheetBinding: Binding<Bool> {
        Binding(
            get: { appState.signInState != .signedOut && appState.pendingInviteCode != nil },
            set: { isPresented in
                if !isPresented { appState.pendingInviteCode = nil }
            }
        )
    }
}
