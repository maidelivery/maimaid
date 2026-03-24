import Foundation

struct DanCategory: Codable, Identifiable {
    let title: String
    let id: String
    let sections: [DanSection]
}

struct DanSection: Codable, Identifiable {
    let title: String?
    let description: String?
    let sheets: [String]
    let sheetDescriptions: [String]?
    
    var id: String { title ?? sheets.joined(separator: "|") }
}

/// A parsed sheet reference from Dan data: "Title|Type|Difficulty"
struct DanSheetRef: Identifiable {
    let title: String
    let type: String
    let difficulty: String
    
    var id: String { "\(title)|\(type)|\(difficulty)" }
    
    init(raw: String) {
        let parts = raw.components(separatedBy: "|")
        if parts.count >= 3 {
            self.title = parts[0]
            self.type = parts[1]
            self.difficulty = parts[2]
        } else {
            // Fallback strategy for malformed strings or placeholders
            self.title = raw
            self.type = ""
            self.difficulty = ""
        }
    }
    
    var isPlaceholder: Bool {
        type.isEmpty || difficulty.isEmpty
    }
}
