import SwiftUI

struct NoteBreakdownItem: Identifiable {
    let label: String
    let count: Int?
    let weight: Double
    let color: Color

    var id: String { label }
}
