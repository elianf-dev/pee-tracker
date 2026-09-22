import FirebaseFunctions
import Foundation
import Observation

enum GroupsError: LocalizedError {
    case malformedResponse

    var errorDescription: String? {
        "The server returned an unexpected response."
    }
}

@Observable
@MainActor
final class GroupsViewModel {
    private(set) var errorMessage: String?
    private(set) var isBusy = false

    func createGroup(name: String) async -> (groupId: String, inviteCode: String)? {
        await run {
            let result = try await Functions.functions()
                .httpsCallable("createGroup")
                .call(["name": name])
            guard
                let data = result.data as? [String: Any],
                let groupId = data["groupId"] as? String,
                let inviteCode = data["inviteCode"] as? String
            else {
                throw GroupsError.malformedResponse
            }
            NotificationManager.shared.subscribe(to: groupId)
            return (groupId, inviteCode)
        }
    }

    func joinGroup(code: String) async -> (groupId: String, name: String)? {
        await run {
            let result = try await Functions.functions()
                .httpsCallable("joinGroup")
                .call(["code": code])
            guard
                let data = result.data as? [String: Any],
                let groupId = data["groupId"] as? String,
                let name = data["name"] as? String
            else {
                throw GroupsError.malformedResponse
            }
            NotificationManager.shared.subscribe(to: groupId)
            return (groupId, name)
        }
    }

    @discardableResult
    func leaveGroup(groupId: String) async -> Bool {
        await run {
            _ = try await Functions.functions()
                .httpsCallable("leaveGroup")
                .call(["groupId": groupId])
            NotificationManager.shared.unsubscribe(from: groupId)
        } != nil
    }

    private func run<T>(_ operation: @escaping () async throws -> T) async -> T? {
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        do {
            return try await operation()
        } catch {
            errorMessage = Self.message(for: error)
            return nil
        }
    }

    private static func message(for error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == FunctionsErrorDomain, nsError.code == FunctionsErrorCode.notFound.rawValue {
            return "That invite code doesn't match a group. Double-check it and try again."
        }
        return error.localizedDescription
    }
}
