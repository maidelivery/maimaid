import SwiftUI

struct ThemeUtils {
    static func colorForDifficulty(_ difficulty: String, _ type: String?) -> Color {
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
        if low.contains("remaster") {
            return Color(light: Color(hex: "#e3bdfc"), dark: Color(hex: "#bf8cfc"))
        }
        if low.contains("master") {
            return Color(light: Color(hex: "#a34ee4"), dark: Color(hex: "#813db4"))
        }
        if type?.lowercased().contains("utage") == true {
            return Color(light: Color(hex: "#ec48e9"), dark: Color(hex: "#bb38b9"))
        }
        
        return .pink
    }
    
    struct AppVersion: Decodable {
        let version: String
        let abbr: String
        let releaseDate: String?
    }
    
    static func versionSortOrder(_ version: String) -> Int {
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        
        // Exact match preferred
        if let index = sequence.firstIndex(of: version) {
            return index
        }
        
        // Fallback to contains
        if let index = sequence.firstIndex(where: { version.contains($0) || $0.contains(version) }) {
            return index
        }
        
        return 999 
    }
    
    static var latestVersion: String {
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        return sequence.last ?? ""
    }
    
    static func versionAbbreviation(_ version: String) -> String {
        guard let data = UserDefaults.standard.data(forKey: "MaimaiVersionsData"),
              let versions = try? JSONDecoder().decode([AppVersion].self, from: data) else {
            return version
        }
        
        if let item = versions.first(where: { $0.version == version || version.contains($0.version) }) {
            return item.abbr
        }
        
        return version
    }
    
    static func categorySortOrder(_ category: String) -> Int {
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiCategorySequence") ?? []
        if let index = sequence.firstIndex(of: category) {
            return index
        }
        return 999
    }
    
    // MARK: - Difficulty Helpers
    
    /// Canonical ordering for difficulties (higher = harder). Used for sorting sheets.
    static func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic":    return 0
        case "advanced": return 1
        case "expert":   return 2
        case "master":   return 3
        case "remaster": return 4
        default:         return -1
        }
    }
    
    /// Abbreviated difficulty name for compact badge display.
    static func diffShort(_ diff: String) -> String {
        switch diff.uppercased() {
        case "BASIC":    return "BAS"
        case "ADVANCED": return "ADV"
        case "EXPERT":   return "EXP"
        case "MASTER":   return "MAS"
        case "REMASTER": return "ReM"
        default:         return diff
        }
    }
    
    /// Maps difficulty string to numeric index (0–4).
    static func mapDifficultyToIndex(_ diff: String) -> Int {
        switch diff.lowercased() {
        case "basic":    return 0
        case "advanced": return 1
        case "expert":   return 2
        case "master":   return 3
        case "remaster": return 4
        default:         return 3
        }
    }
    
    // MARK: - Status Colors
    
    /// Color for Full Combo status badges.
    static func fcColor(_ fc: String) -> Color {
        let low = fc.lowercased()
        if low.contains("ap") { return Color(red: 1.0, green: 0.6, blue: 0.0) }   // gold
        if low.contains("fc") { return Color(red: 0.2, green: 0.75, blue: 0.2) }  // green
        return .secondary
    }
    
    /// Color for Full Sync status badges.
    static func fsColor(_ fs: String) -> Color {
        let low = fs.lowercased()
        if low.contains("fsd") { return Color(red: 0.7, green: 0.3, blue: 1.0) } // purple
        if low.contains("fs") || low.contains("sync") { return Color(red: 0.3, green: 0.5, blue: 1.0) } // blue
        return .secondary
    }
    
    // MARK: - Status Normalization
    
    /// Normalizes FC status codes to display strings (fc→FC, fcp→FC+, ap→AP, app→AP+).
    static func normalizeFC(_ fc: String) -> String {
        switch fc.lowercased() {
        case "app": return "AP+"
        case "ap":  return "AP"
        case "fcp": return "FC+"
        case "fc":  return "FC"
        default:    return fc.uppercased()
        }
    }
    
    /// Normalizes FS status codes to display strings (fs→FS, fsp→FS+, fsd→FDX, fsdp→FDX+).
    static func normalizeFS(_ fs: String) -> String {
        switch fs.lowercased() {
        case "fsdp": return "FDX+"
        case "fsd":  return "FDX"
        case "fsp":  return "FS+"
        case "fs":   return "FS"
        default:     return fs.uppercased()
        }
    }
    
    // MARK: - Rating Colors
    
    static func ratingColor(_ rating: Int) -> Color {
        if rating >= 15000 { return Color(hex: "#FF6100") } // Rainbow base
        if rating >= 14500 { return Color(hex: "#E5E4E2") } // Platinum
        if rating >= 14000 { return Color(hex: "#FFD700") } // Gold
        if rating >= 13000 { return Color(hex: "#C0C0C0") } // Silver
        if rating >= 12000 { return Color(hex: "#CD7F32") } // Bronze
        if rating >= 10000 { return Color(hex: "#D084FF") } // Purple
        if rating >= 7000  { return Color(hex: "#FF5E5E") } // Red
        if rating >= 4000  { return Color(hex: "#FFD400") } // Yellow
        if rating >= 2000  { return Color(hex: "#46D246") } // Green
        if rating >= 1000  { return Color(hex: "#56A6FF") } // Blue
        return .white // White
    }
    
    static func ratingGradient(_ rating: Int) -> LinearGradient {
        if rating >= 15000 {
            // Rainbow
            return LinearGradient(
                colors: [
                    Color(hex: "#FF5E5E"),
                    Color(hex: "#FFBA5E"),
                    Color(hex: "#FFF75E"),
                    Color(hex: "#5EFF5E"),
                    Color(hex: "#5EBAFF"),
                    Color(hex: "#BA5EFF"),
                    Color(hex: "#FF5EBA")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        if rating >= 14500 {
            // Platinum
            return LinearGradient(
                colors: [Color(hex: "#D3D3D3"), Color(hex: "#FFFFFF"), Color(hex: "#D3D3D3")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        if rating >= 14000 {
            // Gold
            return LinearGradient(
                colors: [Color(hex: "#FFD700"), Color(hex: "#FFA500")],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        
        let color = ratingColor(rating)
        return LinearGradient(colors: [color], startPoint: .top, endPoint: .bottom)
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
