import SwiftUI

struct ThemeUtils {
    static func colorForDifficulty(_ difficulty: String) -> Color {
        let low = difficulty.lowercased()
        
        if low.contains("basic") {
            return Color(light: Color(hex: "#36bf63"), dark: Color(hex: "#2a974e"))
        }
        if low.contains("advanced") {
            return Color(light: Color(hex: "#fca13b"), dark: Color(hex: "#c8802d"))
        }
        if low.contains("expert") {
            return Color(light: Color(hex: "#f7536a"), dark: Color(hex: "#c54153"))
        }
        if low.contains("master") {
            return Color(light: Color(hex: "#a34ee4"), dark: Color(hex: "#813db4"))
        }
        if low.contains("remaster") {
            return Color(light: Color(hex: "#be6ff9"), dark: Color(hex: "#9758c5"))
        }
        
        return .pink
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
    
    init(light: Color, dark: Color) {
        self.init(uiColor: UIColor { traitCollection in
            switch traitCollection.userInterfaceStyle {
            case .dark:
                return UIColor(dark)
            default:
                return UIColor(light)
            }
        })
    }
}
