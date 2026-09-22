import Foundation
import Testing
@testable import PeeTracker

@Suite
struct StopwatchViewModelTests {
    @Test
    func elapsedSecondsWithNoStartIsZero() {
        #expect(StopwatchViewModel.elapsedSeconds(from: nil, to: Date()) == 0)
    }

    @Test
    func elapsedSecondsComputesDifference() {
        let start = Date(timeIntervalSince1970: 1_000)
        let end = Date(timeIntervalSince1970: 1_042.5)
        #expect(StopwatchViewModel.elapsedSeconds(from: start, to: end) == 42.5)
    }

    @Test
    func elapsedSecondsClampsNegativeToZero() {
        let start = Date(timeIntervalSince1970: 1_000)
        let end = Date(timeIntervalSince1970: 900)
        #expect(StopwatchViewModel.elapsedSeconds(from: start, to: end) == 0)
    }

    @Test
    @MainActor
    func startSetsRunningStateAndResetsElapsed() {
        let viewModel = StopwatchViewModel()
        let start = Date(timeIntervalSince1970: 2_000)

        viewModel.start(now: start)

        #expect(viewModel.isRunning)
        #expect(viewModel.startDate == start)
        #expect(viewModel.elapsed == 0)
    }

    @Test
    @MainActor
    func stopReturnsElapsedAndClearsRunningState() {
        let viewModel = StopwatchViewModel()
        let start = Date(timeIntervalSince1970: 3_000)

        viewModel.start(now: start)
        let stopped = viewModel.stop(now: start.addingTimeInterval(12))

        #expect(stopped == 12)
        #expect(!viewModel.isRunning)
        #expect(viewModel.elapsed == 12)
    }

    @Test
    @MainActor
    func startWhileAlreadyRunningIsIgnored() {
        let viewModel = StopwatchViewModel()
        let start = Date(timeIntervalSince1970: 4_000)

        viewModel.start(now: start)
        viewModel.start(now: start.addingTimeInterval(100))

        #expect(viewModel.startDate == start)
    }

    @Test
    @MainActor
    func stopWithoutStartReturnsZero() {
        let viewModel = StopwatchViewModel()

        let stopped = viewModel.stop()

        #expect(stopped == 0)
        #expect(!viewModel.isRunning)
    }
}
