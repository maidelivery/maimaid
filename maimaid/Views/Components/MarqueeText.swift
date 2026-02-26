import SwiftUI

struct MarqueeText: View {
    let text: String
    var font: Font = .body
    var fontWeight: Font.Weight = .regular
    var color: Color = .primary
    var spacing: CGFloat = 40
    var speed: Double = 40 // pixels per second
    
    @State private var contentWidth: CGFloat = 0
    @State private var containerWidth: CGFloat = 0
    @State private var isAnimating = false
    
    var body: some View {
        GeometryReader { container in
            HStack(spacing: spacing) {
                Text(text)
                    .font(font)
                    .fontWeight(fontWeight)
                    .foregroundColor(color)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .background(
                        GeometryReader { content in
                            Color.clear
                                .onAppear {
                                    contentWidth = content.size.width
                                }
                                .onChange(of: text) { _, _ in
                                    contentWidth = content.size.width
                                }
                        }
                    )
                
                if contentWidth > container.size.width {
                    Text(text)
                        .font(font)
                        .fontWeight(fontWeight)
                        .foregroundColor(color)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }
            .offset(x: isAnimating ? -(contentWidth + spacing) : 0)
            .onAppear {
                containerWidth = container.size.width
                checkAndStartAnimation()
            }
            .onChange(of: contentWidth) { _, _ in
                checkAndStartAnimation()
            }
            .onChange(of: container.size.width) { _, _ in
                containerWidth = container.size.width
                checkAndStartAnimation()
            }
        }
        .clipped()
    }
    
    private func checkAndStartAnimation() {
        isAnimating = false
        
        guard contentWidth > containerWidth else { return }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            let duration = (contentWidth + spacing) / speed
            withAnimation(.linear(duration: duration).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}
