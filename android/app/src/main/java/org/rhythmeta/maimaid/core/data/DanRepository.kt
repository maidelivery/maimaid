package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class DanRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
) {
    fun observeGroups(unknownLabel: String): Flow<List<DanCategoryGroup>> = combine(
        catalogRepository.danCategories,
        catalogRepository.versions,
    ) { categories, versions ->
        DanCalculator.groupCategories(categories, versions, unknownLabel)
    }.flowOn(Dispatchers.Default)

    fun observeCategory(categoryId: String): Flow<DanCategoryDetail?> = combine(
        catalogRepository.danCategories,
        catalogRepository.songs,
        catalogRepository.sheets,
        scoreRepository.observeActiveScores(),
    ) { categories, songs, sheets, scores ->
        categories.firstOrNull { it.id == categoryId }?.let { category ->
            DanCalculator.buildDetail(category, songs, sheets, scores)
        }
    }.flowOn(Dispatchers.Default)
}
