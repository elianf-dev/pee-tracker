import SwiftUI

private struct PendingLog: Identifiable {
    let startedAt: Date
    let durationSeconds: Int
    var id: Date { startedAt }
}

struct LogEntryView: View {
    @Environment(AppState.self) private var appState
    @State private var stopwatch = StopwatchViewModel()
    @State private var logEntryViewModel = LogEntryViewModel()
    @State private var pendingLog: PendingLog?

    var body: some View {
        VStack(spacing: 32) {
            Text(formattedElapsed)
                .font(.system(size: 64, weight: .bold, design: .rounded))
                .monospacedDigit()

            Button(action: toggleStopwatch) {
                Text(stopwatch.isRunning ? "Stop" : "Start")
                    .font(.title2.weight(.semibold))
                    .frame(width: 160, height: 160)
                    .background(stopwatch.isRunning ? Color.red : Color.blue)
                    .foregroundStyle(.white)
                    .clipShape(Circle())
            }
            .buttonStyle(SpringPressButtonStyle())

            if let errorMessage = logEntryViewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.footnote)
            }
        }
        .padding()
        .sheet(item: $pendingLog) { pending in
            VolumePickerSheet(duration: TimeInterval(pending.durationSeconds)) { volume in
                pendingLog = nil
                guard let groupId = appState.activeGroupId else { return }
                Task {
                    await logEntryViewModel.submitLog(
                        groupId: groupId,
                        startedAt: pending.startedAt,
                        durationSeconds: pending.durationSeconds,
                        volume: volume,
                        displayName: appState.currentUser?.displayName ?? "Anonymous"
                    )
                }
            }
        }
    }

    private var formattedElapsed: String {
        let totalSeconds = Int(stopwatch.elapsed)
        return String(format: "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private func toggleStopwatch() {
        if stopwatch.isRunning {
            guard let startDate = stopwatch.startDate else { return }
            let elapsed = stopwatch.stop()
            pendingLog = PendingLog(startedAt: startDate, durationSeconds: Int(elapsed.rounded()))
        } else {
            stopwatch.start()
        }
    }
}

private struct SpringPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}
