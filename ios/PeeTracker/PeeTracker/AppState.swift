import FirebaseAuth
import FirebaseFirestore
import Foundation
import Observation

@Observable
@MainActor
final class AppState {
    enum SignInState: Equatable {
        case signedOut
        case signedIn(uid: String)
    }

    private(set) var signInState: SignInState = .signedOut
    private(set) var currentUser: UserProfile?

    var activeGroupId: String? {
        currentUser?.currentGroupIds.first
    }

    // Stashed by the peetracker://join/{code} deep-link handler when it fires before sign-in
    // completes; applied once the user reaches JoinGroupView instead of being joined immediately,
    // since joining requires an authenticated callable call.
    var pendingInviteCode: String?

    private var authHandle: AuthStateDidChangeListenerHandle?
    private var profileListener: ListenerRegistration?

    init() {
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.handleAuthChange(user: user)
            }
        }
    }

    private func handleAuthChange(user: FirebaseAuth.User?) {
        profileListener?.remove()
        profileListener = nil

        guard let user else {
            signInState = .signedOut
            currentUser = nil
            return
        }

        signInState = .signedIn(uid: user.uid)
        syncTimezone(uid: user.uid)
        observeProfile(uid: user.uid)
    }

    private func syncTimezone(uid: String) {
        // users/{uid} already exists (created by onUserCreate); this is an update, not
        // a create, and never touches `streak` — both required by firestore.rules.
        Task {
            try? await FirestorePaths.user(uid).setData(
                ["timezone": TimeZone.current.identifier],
                merge: true
            )
        }
    }

    private func observeProfile(uid: String) {
        profileListener = FirestorePaths.user(uid).addSnapshotListener { [weak self] snapshot, _ in
            guard let snapshot, snapshot.exists else { return }
            Task { @MainActor in
                self?.currentUser = try? snapshot.data(as: UserProfile.self)
            }
        }
    }
}
