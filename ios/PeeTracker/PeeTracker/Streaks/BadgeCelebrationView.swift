import SwiftUI

struct BadgeCelebrationView: View {
    let badge: Badge
    let onDismiss: () -> Void

    @State private var isVisible = false

    var body: some View {
        ZStack {
            Color.black.opacity(isVisible ? 0.35 : 0)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                Text(badge.type.copy.emoji)
                    .font(.system(size: 64))
                Text("Badge Earned!")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Text(badge.type.copy.title)
                    .font(.title2.weight(.bold))
            }
            .padding(32)
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .scaleEffect(isVisible ? 1 : 0.6)
            .opacity(isVisible ? 1 : 0)
        }
        .onTapGesture { dismiss() }
        .task {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.65)) {
                isVisible = true
            }
            try? await Task.sleep(for: .seconds(2.5))
            dismiss()
        }
    }

    private func dismiss() {
        withAnimation(.easeOut(duration: 0.2)) {
            isVisible = false
        }
        Task {
            try? await Task.sleep(for: .milliseconds(200))
            onDismiss()
        }
    }
}
