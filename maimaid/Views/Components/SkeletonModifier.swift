import SwiftUI

struct SkeletonModifier: ViewModifier {
    @State private var isAnimating = false
    
    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color.gray.opacity(0.1),
                        Color.gray.opacity(0.3),
                        Color.gray.opacity(0.1)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .rotationEffect(.degrees(15))
                .offset(x: isAnimating ? 200 : -200)
            )
            .clipShape(RoundedRectangle(cornerRadius: 4))
            .onAppear {
                withAnimation(Animation.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                    isAnimating = true
                }
            }
    }
}

extension View {
    func skeleton() -> some View {
        self.modifier(SkeletonModifier())
    }
}
