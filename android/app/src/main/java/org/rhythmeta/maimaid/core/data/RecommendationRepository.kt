package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class RecommendationRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
    private val profileRepository: ProfileRepository,
) {
    fun observeRecommendations(refreshToken: Flow<Int> = flowOf(0)): Flow<RecommendationResponse> {
        val catalog = combine(
            catalogRepository.songs,
            catalogRepository.sheets,
            catalogRepository.versions,
            catalogRepository.chartFit,
        ) { songs, sheets, versions, chartFit ->
            CatalogInput(songs, sheets, versions, chartFit)
        }
        return combine(
            profileRepository.activeProfile,
            scoreRepository.observeActiveScores(),
            catalog,
            refreshToken,
        ) { profile, scores, input, _ ->
            profile?.let {
                RecommendationCalculator.calculate(
                    songs = input.songs,
                    sheets = input.sheets,
                    scores = scores,
                    versions = input.versions,
                    profile = it,
                    chartFit = input.chartFit,
                )
            } ?: RecommendationResponse()
        }.flowOn(Dispatchers.Default)
    }

    private data class CatalogInput(
        val songs: List<SongEntity>,
        val sheets: List<SheetEntity>,
        val versions: List<GameVersionEntity>,
        val chartFit: StaticBundleResponse.ChartFitPayload,
    )
}
