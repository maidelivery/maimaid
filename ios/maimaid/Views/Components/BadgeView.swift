import SwiftUI

/// Reusable pill badge for difficulty, FC, FS, and type indicators.
struct BadgeView: View {
    let text: String
    var background: Color = .blue
    var foreground: Color = .white
    
    var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .heavy))
            .fixedSize()
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(background)
            .foregroundStyle(foreground)
            .cornerRadius(4)
    }
}
