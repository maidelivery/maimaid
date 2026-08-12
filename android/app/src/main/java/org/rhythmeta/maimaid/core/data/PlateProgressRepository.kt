package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class PlateProgressSelection(
    val groupId: String? = null,
    val difficulty: String = "master",
    val plateType: PlateType = PlateType.Sho,
)

class PlateProgressRepository(
    private val catalogRepository: CatalogRepository,
    private val scoreRepository: ScoreRepository,
) {
    fun observePlateProgress(selection: Flow<PlateProgressSelection>): Flow<PlateProgressResponse> {
        val catalog = combine(
            catalogRepository.songs,
            catalogRepository.sheets,
            catalogRepository.versions,
        ) { songs, sheets, versions -> Triple(songs, sheets, versions) }
        return combine(catalog, scoreRepository.observeActiveScores(), selection) { input, scores, selected ->
            PlateProgressCalculator.calculate(
                songs = input.first,
                sheets = input.second,
                scores = scores,
                versions = input.third,
                groupId = selected.groupId,
                difficulty = selected.difficulty,
                plateType = selected.plateType,
            )
        }.flowOn(Dispatchers.Default)
    }
}
