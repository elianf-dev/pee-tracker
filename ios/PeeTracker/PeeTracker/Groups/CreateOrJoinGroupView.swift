import SwiftUI

struct CreateOrJoinGroupView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                NavigationLink {
                    CreateGroupView()
                } label: {
                    OnboardingCard(
                        title: "Create a Group",
                        subtitle: "Start a new group and invite friends",
                        systemImage: "plus.circle.fill"
                    )
                }

                NavigationLink {
                    JoinGroupView()
                } label: {
                    OnboardingCard(
                        title: "Join with Code",
                        subtitle: "Enter an invite code from a friend",
                        systemImage: "person.badge.plus"
                    )
                }
            }
            .padding()
            .navigationTitle("Get Started")
        }
    }
}

private struct OnboardingCard: View {
    let title: String
    let subtitle: String
    let systemImage: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: systemImage)
                .font(.system(size: 32))
                .frame(width: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3.weight(.semibold))
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .foregroundStyle(.primary)
    }
}
