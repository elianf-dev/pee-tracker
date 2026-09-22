import SwiftUI

struct JoinGroupView: View {
    @State private var viewModel = GroupsViewModel()
    @State private var code: String
    @State private var didJoin = false

    init(prefilledCode: String = "") {
        _code = State(initialValue: prefilledCode.uppercased())
    }

    var body: some View {
        VStack(spacing: 20) {
            TextField("Invite code (e.g. PEE-4X9K)", text: $code)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .onChange(of: code) { _, newValue in
                    let uppercased = newValue.uppercased()
                    code = String(uppercased.prefix(8))
                }

            Button("Join") {
                Task {
                    if await viewModel.joinGroup(code: code) != nil {
                        didJoin = true
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
            .disabled(!isValidCode || viewModel.isBusy)

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.footnote)
            }

            if didJoin {
                Text("You're in! Loading your group…")
                    .foregroundStyle(.secondary)
                    .font(.footnote)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Join a Group")
        .disabled(viewModel.isBusy)
    }

    private var isValidCode: Bool {
        (6...8).contains(code.count)
    }
}
