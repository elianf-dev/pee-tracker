import SwiftUI

private struct CreatedInvite: Identifiable, Hashable {
    let inviteCode: String
    var id: String { inviteCode }
}

struct CreateGroupView: View {
    @State private var viewModel = GroupsViewModel()
    @State private var name = ""
    @State private var createdInvite: CreatedInvite?

    var body: some View {
        VStack(spacing: 20) {
            TextField("Group name", text: $name)
                .textFieldStyle(.roundedBorder)
                .onChange(of: name) { _, newValue in
                    if newValue.count > 40 {
                        name = String(newValue.prefix(40))
                    }
                }

            Button("Create") {
                Task {
                    if let result = await viewModel.createGroup(name: name) {
                        createdInvite = CreatedInvite(inviteCode: result.inviteCode)
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
            .disabled(!isValidName || viewModel.isBusy)

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.footnote)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Create a Group")
        .disabled(viewModel.isBusy)
        .navigationDestination(item: $createdInvite) { invite in
            InviteShareView(inviteCode: invite.inviteCode)
        }
    }

    private var isValidName: Bool {
        (1...40).contains(name.count)
    }
}
