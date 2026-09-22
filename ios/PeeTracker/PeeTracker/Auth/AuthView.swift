import AuthenticationServices
import SwiftUI

struct AuthView: View {
    @State private var viewModel = AuthViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    Picker("Mode", selection: $viewModel.mode) {
                        Text("Sign In").tag(AuthViewModel.Mode.signIn)
                        Text("Sign Up").tag(AuthViewModel.Mode.signUp)
                    }
                    .pickerStyle(.segmented)

                    VStack(spacing: 12) {
                        TextField("Email", text: $viewModel.email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)

                        SecureField("Password", text: $viewModel.password)
                            .textContentType(viewModel.mode == .signUp ? .newPassword : .password)
                            .textFieldStyle(.roundedBorder)

                        Button(viewModel.mode == .signUp ? "Create Account" : "Sign In") {
                            Task { await viewModel.submitEmailForm() }
                        }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                        .disabled(viewModel.email.isEmpty || viewModel.password.isEmpty || viewModel.isBusy)
                    }

                    SignInWithAppleButton(.signIn) { request in
                        viewModel.prepareAppleRequest(request)
                    } onCompletion: { result in
                        Task { await viewModel.handleAppleCompletion(result) }
                    }
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 44)

                    Button {
                        guard let presenter = UIApplication.shared.topViewController else { return }
                        Task { await viewModel.signInWithGoogle(presenting: presenter) }
                    } label: {
                        Label("Sign in with Google", systemImage: "globe")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }
                .padding()
            }
            .navigationTitle("PeeTracker")
            .disabled(viewModel.isBusy)
        }
    }
}

private extension UIApplication {
    var topViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
    }
}
