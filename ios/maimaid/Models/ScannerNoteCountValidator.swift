nonisolated enum ScannerNoteCountValidator {
    static func isCompatible(maxDxScore: Int?, sheetTotal: Int?) -> Bool {
        guard let maxDxScore, maxDxScore > 0, let sheetTotal else {
            return true
        }

        return maxDxScore / 3 <= sheetTotal
    }
}
