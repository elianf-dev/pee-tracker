import SwiftUI

struct LeaderboardView: View {
    @State private var viewModel: LeaderboardViewModel

    init(groupId: String, uid: String) {
        _viewModel = State(initialValue: LeaderboardViewModel(groupId: groupId, uid: uid))
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Period", selection: $viewModel.selectedPeriod) {
                ForEach(LeaderboardPeriod.allCases, id: \.self) { period in
                    Text(period.label).tag(period)
                }
            }
            .pickerStyle(.segmented)
            .padding()

            if viewModel.rows.isEmpty {
                emptyState
            } else {
                List(rankedRows, id: \.row.id) { ranked in
                    LeaderboardRowView(rank: ranked.rank, row: ranked.row, isCurrentUser: ranked.row.uid == viewModel.uid)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Leaderboard")
        .onDisappear { viewModel.stop() }
    }

    private var rankedRows: [(rank: Int, row: LeaderboardRow)] {
        viewModel.rows.enumerated().map { (rank: $0.offset + 1, row: $0.element) }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("🚽")
                .font(.system(size: 48))
            Text(emptyMessage)
                .font(.headline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }

    private var emptyMessage: String {
        switch viewModel.selectedPeriod {
        case .daily:
            "No trips logged yet today — go take the lead!"
        case .weekly:
            "No trips logged yet this week — go take the lead!"
        }
    }
}

private struct LeaderboardRowView: View {
    let rank: Int
    let row: LeaderboardRow
    let isCurrentUser: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text("\(rank)")
                .font(.headline)
                .frame(width: 28, alignment: .leading)

            if rank == 1 {
                Image(systemName: "trophy.fill")
                    .foregroundStyle(.yellow)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(row.entry.displayName)
                    .font(isCurrentUser ? .body.bold() : .body)
                Text("\(row.entry.count) trip\(row.entry.count == 1 ? "" : "s") \u{00B7} \(Self.formattedDuration(row.entry.totalDurationSeconds))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if isCurrentUser {
                Text("You")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.blue)
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(isCurrentUser ? Color.blue.opacity(0.12) : Color.clear)
    }

    private static func formattedDuration(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let remainingSeconds = seconds % 60
        if hours > 0 {
            return String(format: "%dh %02dm", hours, minutes)
        }
        if minutes > 0 {
            return String(format: "%dm %02ds", minutes, remainingSeconds)
        }
        return String(format: "%ds", remainingSeconds)
    }
}
