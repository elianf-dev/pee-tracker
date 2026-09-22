import SwiftUI

struct VolumePickerSheet: View {
    let duration: TimeInterval
    let onSubmit: (Volume) -> Void

    @State private var selection: Volume = .medium
    @State private var hasSubmitted = false
    @State private var isConfirmed = false

    var body: some View {
        VStack(spacing: 20) {
            Text("\(Int(duration))s — how much?")
                .font(.headline)

            Picker("Volume", selection: $selection) {
                ForEach(Volume.allCases, id: \.self) { volume in
                    Text(volume.rawValue.capitalized).tag(volume)
                }
            }
            .pickerStyle(.segmented)
            .scaleEffect(isConfirmed ? 1.05 : 1)
            .onChange(of: selection) { _, newValue in
                withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                    isConfirmed = true
                }
                submit(newValue)
            }
        }
        .padding()
        .presentationDetents([.height(160)])
        .onDisappear {
            // Covers swipe-down / tap-outside dismissal; segment taps already submitted
            // above, and `hasSubmitted` prevents this from double-submitting in that case.
            submit(selection)
        }
    }

    private func submit(_ volume: Volume) {
        guard !hasSubmitted else { return }
        hasSubmitted = true
        onSubmit(volume)
    }
}
