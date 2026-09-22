import AuthenticationServices
import CryptoKit
import FirebaseAuth
import Foundation
import GoogleSignIn
import Observation
import UIKit

enum AuthError: LocalizedError {
    case missingAppleNonce
    case missingAppleToken
    case missingGoogleToken

    var errorDescription: String? {
        switch self {
        case .missingAppleNonce: "Apple sign-in was not prepared correctly."
        case .missingAppleToken: "Apple sign-in did not return a usable token."
        case .missingGoogleToken: "Google sign-in did not return a usable token."
        }
    }
}

@Observable
@MainActor
final class AuthViewModel {
    enum Mode {
        case signIn
        case signUp
    }

    var mode: Mode = .signIn
    var email = ""
    var password = ""
    private(set) var errorMessage: String?
    private(set) var isBusy = false
    private(set) var signedIn = false

    private var currentAppleNonce: String?

    func submitEmailForm() async {
        await run { [self] in
            switch mode {
            case .signIn:
                try await Auth.auth().signIn(withEmail: email, password: password)
            case .signUp:
                try await Auth.auth().createUser(withEmail: email, password: password)
            }
        }
    }

    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = Self.randomNonce()
        currentAppleNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
    }

    func handleAppleCompletion(_ result: Result<ASAuthorization, Error>) async {
        await run { [self] in
            guard let nonce = currentAppleNonce else {
                throw AuthError.missingAppleNonce
            }
            currentAppleNonce = nil

            let authorization = try result.get()
            guard
                let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let identityToken = credential.identityToken,
                let tokenString = String(data: identityToken, encoding: .utf8)
            else {
                throw AuthError.missingAppleToken
            }

            let firebaseCredential = OAuthProvider.appleCredential(
                withIDToken: tokenString,
                rawNonce: nonce,
                fullName: credential.fullName
            )
            try await Auth.auth().signIn(with: firebaseCredential)
        }
    }

    func signInWithGoogle(presenting: UIViewController) async {
        await run {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenting)
            guard let idToken = result.user.idToken?.tokenString else {
                throw AuthError.missingGoogleToken
            }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            try await Auth.auth().signIn(with: credential)
        }
    }

    private func run(_ operation: @escaping () async throws -> Void) async {
        isBusy = true
        errorMessage = nil
        do {
            try await operation()
            signedIn = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isBusy = false
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz")
        var result = ""
        var remainingLength = length
        while remainingLength > 0 {
            var randoms = [UInt8](repeating: 0, count: 16)
            let status = SecRandomCopyBytes(kSecRandomDefault, randoms.count, &randoms)
            precondition(status == errSecSuccess, "Unable to generate a secure nonce")
            for random in randoms where remainingLength > 0 {
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .compactMap { String(format: "%02x", $0) }
            .joined()
    }
}
