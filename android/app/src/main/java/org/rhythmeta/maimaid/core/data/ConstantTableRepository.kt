package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ConstantTableRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
    private val profileRepository: ProfileRepository,
) {
    fun observeConstantTable(): Flow<ConstantTableResponse> = combine(
        catalogRepository.songs,
        catalogRepository.sheets,
        scoreRepository.observeActiveScores(),
        profileRepository.activeProfile,
    ) { songs, sheets, scores, profile ->
        ConstantTableCalculator.build(
            songs = songs,
            sheets = sheets,
            scores = scores,
            userName = profile?.name,
            server = profile?.server ?: "jp",
        )
    }.flowOn(Dispatchers.Default)
}
