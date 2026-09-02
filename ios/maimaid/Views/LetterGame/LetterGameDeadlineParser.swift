import Foundation

enum LetterGameDeadlineParser {
    static func date(from value: String) -> Date? {
        let fractional = Date.ISO8601FormatStyle(includingFractionalSeconds: true, timeZone: .gmt)
        if let date = try? Date(value, strategy: fractional) {
            return date
        }
        return try? Date(value, strategy: .iso8601)
    }
}
