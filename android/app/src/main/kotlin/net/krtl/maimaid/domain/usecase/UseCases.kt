package net.krtl.maimaid.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.krtl.maimaid.domain.model.B50Result
import net.krtl.maimaid.domain.model.HomeSummary
import net.krtl.maimaid.domain.model.PlateProgressItem
import net.krtl.maimaid.domain.model.RecommendationResult
import net.krtl.maimaid.domain.model.StaticSyncOptions
import net.krtl.maimaid.domain.model.StaticSyncStatus
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.repository.PreferencesRepository
import net.krtl.maimaid.domain.repository.ProfileRepository
import net.krtl.maimaid.domain.repository.RecommendationRepository
import net.krtl.maimaid.domain.repository.ScoreRepository
import net.krtl.maimaid.domain.repository.StaticDataRepository

class SyncStaticDataUseCase(private val staticDataRepository: StaticDataRepository) {
    val status: Flow<StaticSyncStatus> = staticDataRepository.syncStatus
    suspend operator fun invoke(options: StaticSyncOptions) = staticDataRepository.syncStaticData(options)
}

class CalculateB50UseCase(
    private val staticDataRepository: StaticDataRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val recommendationRepository: RecommendationRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(profile: UserProfile? = null): B50Result {
        val targetProfile = profile ?: profileRepository.ensureActiveProfile()
        val songs = staticDataRepository.observeSongs().first()
        val preferences = preferencesRepository.preferences.first()
        val scores = scoreRepository.getScores(targetProfile.id)
        return recommendationRepository.getB50(targetProfile, songs, scores, preferences)
    }
}

class GetRecommendationsUseCase(
    private val staticDataRepository: StaticDataRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val recommendationRepository: RecommendationRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(profile: UserProfile? = null): RecommendationResult {
        val targetProfile = profile ?: profileRepository.ensureActiveProfile()
        val songs = staticDataRepository.observeSongs().first()
        val preferences = preferencesRepository.preferences.first()
        val scores = scoreRepository.getScores(targetProfile.id)
        return recommendationRepository.getRecommendations(targetProfile, songs, scores, preferences)
    }
}

class GetPlateProgressUseCase(
    private val staticDataRepository: StaticDataRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val recommendationRepository: RecommendationRepository
) {
    suspend operator fun invoke(profile: UserProfile? = null): List<PlateProgressItem> {
        val targetProfile = profile ?: profileRepository.ensureActiveProfile()
        val songs = staticDataRepository.observeSongs().first()
        val scores = scoreRepository.getScores(targetProfile.id)
        return recommendationRepository.getPlateProgress(songs, scores)
    }
}

class GetHomeSummaryUseCase(private val recommendationRepository: RecommendationRepository) {
    suspend operator fun invoke(): HomeSummary = recommendationRepository.getHomeSummary()
}
