package org.rhythmeta.maimaid.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.OtogameImportPolicy
import org.rhythmeta.maimaid.core.data.OtogameProfileUnavailableException
import org.rhythmeta.maimaid.core.data.OtogameUnauthorizedException

enum class OtogameImportPhase {
    Idle,
    Synchronizing,
}

enum class OtogameImportOutcome {
    Imported,
    NoChanges,
    LoginRequired,
    IneligibleProfile,
    Failed,
}

data class OtogameImportUiState(
    val profileName: String? = null,
    val isEligibleProfile: Boolean = false,
    val hasSession: Boolean = false,
    val phase: OtogameImportPhase = OtogameImportPhase.Idle,
    val outcome: OtogameImportOutcome? = null,
    val fetchedCount: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val unmatchedCount: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
) {
    val isBusy: Boolean get() = phase != OtogameImportPhase.Idle
}

class OtogameImportViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OtogameImportUiState())
    val state = mutableState.asStateFlow()

    @Volatile
    private var authorizationHeader: String? = null

    init {
        viewModelScope.launch {
            var previousProfileId: String? = null
            container.profileRepository.activeProfile.collectLatest { profile ->
                val profileChanged = previousProfileId != profile?.id
                previousProfileId = profile?.id
                mutableState.update { current ->
                    current.copy(
                        profileName = profile?.name,
                        isEligibleProfile = OtogameImportPolicy.isEligibleServer(profile?.server),
                        outcome = current.outcome.takeUnless { profileChanged },
                        fetchedCount = current.fetchedCount.takeUnless { profileChanged } ?: 0,
                        importedCount = current.importedCount.takeUnless { profileChanged } ?: 0,
                        duplicateCount = current.duplicateCount.takeUnless { profileChanged } ?: 0,
                        unmatchedCount = current.unmatchedCount.takeUnless { profileChanged } ?: 0,
                        currentPage = current.currentPage.takeUnless { profileChanged } ?: 0,
                        totalPages = current.totalPages.takeUnless { profileChanged } ?: 0,
                    )
                }
            }
        }
    }

    fun captureAuthorizationHeader(value: String) {
        val header = value.trim().takeIf { it.startsWith("Bearer ", ignoreCase = true) } ?: return
        authorizationHeader = header
        mutableState.update { it.copy(hasSession = true, outcome = null) }
    }

    fun synchronize() {
        val current = mutableState.value
        if (current.isBusy) return
        if (!current.isEligibleProfile) {
            mutableState.update { it.copy(outcome = OtogameImportOutcome.IneligibleProfile) }
            return
        }
        val header = authorizationHeader
        if (header == null) {
            mutableState.update { it.copy(outcome = OtogameImportOutcome.LoginRequired) }
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    phase = OtogameImportPhase.Synchronizing,
                    outcome = null,
                    fetchedCount = 0,
                    importedCount = 0,
                    duplicateCount = 0,
                    unmatchedCount = 0,
                    currentPage = 0,
                    totalPages = 0,
                )
            }
            try {
                val result = container.otogameImportService.importRecent(header) { currentPage, totalPages ->
                    mutableState.update {
                        it.copy(currentPage = currentPage, totalPages = totalPages)
                    }
                }
                mutableState.update {
                    it.copy(
                        phase = OtogameImportPhase.Idle,
                        outcome = if (result.importedCount > 0) {
                            OtogameImportOutcome.Imported
                        } else {
                            OtogameImportOutcome.NoChanges
                        },
                        fetchedCount = result.fetchedCount,
                        importedCount = result.importedCount,
                        duplicateCount = result.duplicateCount,
                        unmatchedCount = result.unmatchedCount,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: OtogameUnauthorizedException) {
                authorizationHeader = null
                mutableState.update {
                    it.copy(
                        phase = OtogameImportPhase.Idle,
                        hasSession = false,
                        outcome = OtogameImportOutcome.LoginRequired,
                    )
                }
            } catch (_: OtogameProfileUnavailableException) {
                mutableState.update {
                    it.copy(
                        phase = OtogameImportPhase.Idle,
                        outcome = OtogameImportOutcome.IneligibleProfile,
                    )
                }
            } catch (error: Exception) {
                Log.e(
                    DiagnosticTag,
                    "Import failed: ${error.exceptionTypeChain()}; ${error.sanitizedDiagnosticMessage()}",
                )
                mutableState.update {
                    it.copy(
                        phase = OtogameImportPhase.Idle,
                        outcome = OtogameImportOutcome.Failed,
                    )
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OtogameImportViewModel::class.java))
            return OtogameImportViewModel(container) as T
        }
    }

    private fun Throwable.exceptionTypeChain(): String =
        generateSequence(this) { it.cause }
            .joinToString(" <- ") { it::class.java.simpleName }

    private fun Throwable.sanitizedDiagnosticMessage(): String =
        generateSequence(this) { it.cause }
            .mapNotNull(Throwable::message)
            .lastOrNull()
            ?.substringBefore("JSON input:")
            ?.replace('\n', ' ')
            ?.take(500)
            .orEmpty()

    private companion object {
        const val DiagnosticTag = "OtogameImport"
    }
}
