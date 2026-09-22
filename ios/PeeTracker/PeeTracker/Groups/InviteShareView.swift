import SwiftUI

struct InviteShareView: View {
    let inviteCode: String

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text("Your invite code")
                .font(.headline)
                .foregroundStyle(.secondary)

            Text(inviteCode)
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .monospaced()

            ShareLink(item: shareText) {
                Label("Share Invite", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            Spacer()

            Button("Continue") {
                dismiss()
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
        }
        .padding()
        .navigationTitle("Group Created")
        .navigationBarBackButtonHidden(true)
    }

    private var shareText: String {
        "Join my PeeTracker group! Code: \(inviteCode)"
    }
}
