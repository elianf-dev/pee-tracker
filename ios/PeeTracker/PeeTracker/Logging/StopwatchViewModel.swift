import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class StopwatchViewModel {
    private(set) var startDate: Date?
    private(set) var elapsed: TimeInterval = 0
    private(set) var isRunning = false

    private var ticker: Timer?

    func start(now: Date = Date()) {
        guard !isRunning else { return }
        startDate = now
        elapsed = 0
        isRunning = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        ticker = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let startDate = self.startDate else { return }
                self.elapsed = Self.elapsedSeconds(from: startDate, to: Date())
            }
        }
    }

    @discardableResult
    func stop(now: Date = Date()) -> TimeInterval {
        ticker?.invalidate()
        ticker = nil
        isRunning = false
        elapsed = Self.elapsedSeconds(from: startDate, to: now)
        return elapsed
    }

    nonisolated static func elapsedSeconds(from start: Date?, to end: Date) -> TimeInterval {
        guard let start else { return 0 }
        return max(0, end.timeIntervalSince(start))
    }
}
