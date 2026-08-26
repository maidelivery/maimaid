package org.rhythmeta.maimaid.ui.scanner

import org.rhythmeta.maimaid.core.ml.ModelDownloadProgress

sealed interface ScannerModelState {
    data class Checking(val cachedModelsAvailable: Boolean = false) : ScannerModelState
    data class DownloadRequired(val totalBytes: Long) : ScannerModelState
    data class UpdateAvailable(val totalBytes: Long) : ScannerModelState
    data class Downloading(
        val progress: ModelDownloadProgress,
        val isUpdate: Boolean,
    ) : ScannerModelState
    data class Ready(val offline: Boolean = false) : ScannerModelState
    data class Failed(
        val message: String,
        val cachedModelsAvailable: Boolean,
    ) : ScannerModelState
}
