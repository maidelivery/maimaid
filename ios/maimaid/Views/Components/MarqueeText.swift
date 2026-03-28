import SwiftUI

struct MarqueeText: View {
    let text: String
    var font: Font = .body
    var fontWeight: Font.Weight = .regular
    var color: Color = .primary
    var alignment: Alignment = .leading
    var spacing: CGFloat = 60
    var speed: Double = 30 // pixels per second (slower default)
    var initialDelay: Double = 2.0 // pause on first character
    
    @State private var contentWidth: CGFloat = 0
    @State private var containerWidth: CGFloat = 0
    @State private var offset: CGFloat = 0
    @State private var marqueeTask: Task<Void, Never>?
    
    private var needsScroll: Bool {
        contentWidth > containerWidth && containerWidth > 0
    }
    
    var body: some View {
        GeometryReader { container in
            let cw = container.size.width
            
            HStack(spacing: spacing) {
                textLabel
                    .background(
                        GeometryReader { content in
                            Color.clear
                                .onAppear {
                                    contentWidth = content.size.width
                                    containerWidth = cw
                                }
                                .onChange(of: text) { _, _ in
                                    contentWidth = content.size.width
                                }
                        }
                    )
                
                // Duplicate for seamless loop
                if needsScroll {
                    textLabel
                }
            }
            .offset(x: offset)
            .frame(width: cw, alignment: needsScroll ? .leading : alignment)
            .onAppear {
                containerWidth = cw
                startMarquee()
            }
            .onChange(of: contentWidth) { _, _ in
                startMarquee()
            }
            .onChange(of: cw) { _, newWidth in
                containerWidth = newWidth
                startMarquee()
            }
            .onDisappear {
                stopMarquee()
            }
        }
        .clipped()
    }
    
    private var textLabel: some View {
        Text(text)
            .font(font)
            .fontWeight(fontWeight)
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
    }
    
    private func startMarquee() {
        stopMarquee()
        offset = 0
        
        guard needsScroll else { return }
        
        let totalDistance = contentWidth + spacing
        let duration = totalDistance / speed
        
        marqueeTask = Task { @MainActor in
            while !Task.isCancelled {
                // Pause at the beginning
                try? await Task.sleep(nanoseconds: UInt64(initialDelay * 1_000_000_000))
                if Task.isCancelled { break }
                
                withAnimation(.linear(duration: duration)) {
                    offset = -totalDistance
                }
                
                try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
                if Task.isCancelled { break }
                
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    offset = 0
                }
            }
        }
    }
    
    private func stopMarquee() {
        marqueeTask?.cancel()
        marqueeTask = nil
        offset = 0
    }
}
