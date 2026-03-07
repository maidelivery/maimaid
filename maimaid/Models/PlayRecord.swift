import Foundation
import SwiftData

@Model
final class PlayRecord {
    var id: UUID
    var sheetId: String
    var rate: Double
    var rank: String
    var dxScore: Int = 0
    var fc: String?
    var fs: String?
    var playDate: Date
    
    var sheet: Sheet?
    
    init(id: UUID = UUID(), sheetId: String, rate: Double, rank: String, dxScore: Int = 0, fc: String? = nil, fs: String? = nil, playDate: Date = Date()) {
        self.id = id
        self.sheetId = sheetId
        self.rate = rate
        self.rank = rank
        self.dxScore = dxScore
        self.fc = fc
        self.fs = fs
        self.playDate = playDate
    }
}
