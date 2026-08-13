package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ScoreQueryRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
    private val communityAliasService: CommunityAliasService,
) {
    fun observeScoreQuery(): Flow<ScoreQueryResponse> = combine(
        catalogRepository.songs,
        catalogRepository.sheets,
        scoreRepository.observeActiveScores(),
        communityAliasService.searchableAliases,
    ) { songs, sheets, scores, aliases ->
        ScoreQueryCalculator.build(songs, sheets, scores, aliases)
    }
}
