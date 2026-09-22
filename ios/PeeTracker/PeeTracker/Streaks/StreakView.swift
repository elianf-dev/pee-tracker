import SwiftUI

struct StreakView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: BadgesViewModel

    init(groupId: String, uid: String) {
        _viewModel = State(initialValue: BadgesViewModel(groupId: groupId, uid: uid))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                if let streak = appState.currentUser?.streak {
                    StreakCounterCard(streak: streak)
                }
                BadgeGrid(earnedBadges: viewModel.myBadges)
            }
            .padding()
        }
        .navigationTitle("Streaks & Badges")
        .onDisappear { viewModel.stop() }
        .overlay {
            if let badge = viewModel.newlyAwardedBadge {
                BadgeCelebrationView(badge: badge) {
                    viewModel.dismissCelebration()
                }
            }
        }
    }
}

private struct StreakCounterCard: View {
    let streak: UserProfile.Streak

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(.orange)
                Text("\(streak.current)")
                    .font(.system(size: 56, weight: .bold, design: .rounded))
            }

            Text(streak.current == 1 ? "day streak" : "day streak")
                .font(.headline)
                .foregroundStyle(.secondary)

            Text(funMessage)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            if streak.longest > streak.current {
                Text("Longest streak: \(streak.longest) day\(streak.longest == 1 ? "" : "s")")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var funMessage: String {
        switch streak.current {
        case 0:
            "Log today to start a new streak!"
        case 1:
            "Nice start — keep it going!"
        case 2..<7:
            "You're on a roll!"
        case 7..<30:
            "A full week strong. Impressive."
        default:
            "Streak legend status unlocked."
        }
    }
}

private struct BadgeGrid: View {
    let earnedBadges: [Badge]

    private let columns = [GridItem(.adaptive(minimum: 100), spacing: 16)]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Badges")
                .font(.headline)

            LazyVGrid(columns: columns, spacing: 16) {
                ForEach(BadgeType.allCases, id: \.self) { type in
                    // Badges of period types (camel_of_day, etc.) can be earned repeatedly across
                    // different periods; `badges` is already sorted newest-first, so `.first`
                    // surfaces the most recent award for display.
                    BadgeTile(type: type, earned: earnedBadges.first { $0.type == type })
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct BadgeTile: View {
    let type: BadgeType
    let earned: Badge?

    var body: some View {
        VStack(spacing: 6) {
            Text(type.copy.emoji)
                .font(.system(size: 36))
                .opacity(earned == nil ? 0.25 : 1)
                .overlay(alignment: .topTrailing) {
                    if earned == nil {
                        Image(systemName: "lock.fill")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

            Text(type.copy.title)
                .font(.caption.weight(.semibold))
                .multilineTextAlignment(.center)
                .foregroundStyle(earned == nil ? .secondary : .primary)

            if let earned, let awardedAt = earned.awardedAt {
                Text(awardedAt.dateValue().formatted(date: .abbreviated, time: .omitted))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
