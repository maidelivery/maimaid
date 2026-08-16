package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ScoreQueryRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
    private val communityAliasService: CommunityAliasService,
    private val profileRepository: ProfileRepository,
) {
    fun observeScoreQuery(): Flow<ScoreQueryResponse> = combine(
        catalogRepository.songs,
        catalogRepository.sheets,
        scoreRepository.observeActiveScores(),
        communityAliasService.searchableAliases,
        profileRepository.activeProfile,
    ) { songs, sheets, scores, aliases, profile ->
        ScoreQueryCalculator.build(
            songs = songs,
            sheets = sheets,
            scores = scores,
            aliases = aliases,
            server = profile?.server ?: "jp",
        )
    }
}
