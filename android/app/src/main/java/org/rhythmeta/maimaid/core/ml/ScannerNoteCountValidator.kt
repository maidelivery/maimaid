package org.rhythmeta.maimaid.core.ml

internal object ScannerNoteCountValidator {
    fun isCompatible(maxDxScore: Int?, sheetTotal: Int?): Boolean {
        if (maxDxScore == null || maxDxScore <= 0 || sheetTotal == null) return true
        return maxDxScore / 3 <= sheetTotal
    }
}
