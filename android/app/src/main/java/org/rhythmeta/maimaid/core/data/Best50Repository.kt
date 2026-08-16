package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import org.rhythmeta.maimaid.core.database.Best50Row
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.MaimaidDatabase

data class Best50State(
    val total: Int = 0,
    val b35: List<RatingUtils.Entry> = emptyList(),
    val b15: List<RatingUtils.Entry> = emptyList(),
    val latestVersion: String? = null,
    val isEmpty: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class Best50Repository(
    private val database: MaimaidDatabase,
    private val profileRepository: ProfileRepository,
) {
    fun observeBest50(
        versionOverride: String? = null,
        b35CountOverride: Int? = null,
        b15CountOverride: Int? = null,
    ): Flow<Best50State> =
        profileRepository.activeProfile.flatMapLatest { profile ->
            if (profile == null) {
                flowOf(Best50State())
            } else {
                combine(
                    database.scoreDao().observeBest50Rows(profile.id),
                    database.catalogDao().observeVersions(),
                    database.catalogDao().observeSongs(),
                    database.catalogDao().observeSheets(),
                ) { rows, versions, songs, sheets ->
                    calculateBest50(
                        rows = rows,
                        versions = versions,
                        songs = songs,
                        sheets = sheets,
                        server = profile.server,
                        b35Count = b35CountOverride ?: profile.b35Count,
                        b15Count = b15CountOverride ?: profile.b15Count,
                        versionOverride = versionOverride,
                    )
                }
            }
        }.flowOn(Dispatchers.Default)
}

internal fun calculateBest50(
    rows: List<Best50Row>,
    versions: List<GameVersionEntity>,
    songs: List<org.rhythmeta.maimaid.core.database.SongEntity>,
    sheets: List<org.rhythmeta.maimaid.core.database.SheetEntity>,
    server: String,
    b35Count: Int,
    b15Count: Int,
    versionOverride: String?,
): Best50State {
    val versionNames = versions.sortedBy { it.sortOrder }.map { it.name }
    val latest = versionOverride?.takeIf { candidate ->
        versionNames.any { it.equals(candidate, ignoreCase = true) }
    } ?: RatingUtils.latestVersionForServer(songs, sheets, versions, server)
    val afterCircle = RatingUtils.isAfterCircle(latest, versionNames)
    val sheetsByKey = sheets.filterNot { it.isRemoved }.associateBy { it.sheetKey }
    val b35 = mutableListOf<RatingUtils.Entry>()
    val b15 = mutableListOf<RatingUtils.Entry>()
    rows.forEach { row ->
        if (row.category.contains("utage", ignoreCase = true) || row.type.contains("utage", ignoreCase = true)) return@forEach
        val sheet = sheetsByKey[row.sheetKey] ?: return@forEach
        if (!ServerChartPolicy.isPlayable(sheet, server)) return@forEach
        val metadata = ServerChartPolicy.metadata(sheet, server)
        val isNew = RatingUtils.category(
            metadata.version ?: row.songVersion,
            latest,
            server,
            true,
            versionNames,
        ) ?: return@forEach
        val level = metadata.ratingLevel ?: return@forEach
        val rating = RatingUtils.calculate(level, row.achievement, row.fc, afterCircle)
        if (rating <= 0) return@forEach
        (if (isNew) b15 else b35).add(
            RatingUtils.Entry(
                sheetKey = row.sheetKey,
                songIdentifier = row.songIdentifier,
                songId = row.songId,
                title = row.title,
                imageName = row.imageName,
                achievement = row.achievement,
                rating = rating,
                level = level,
                difficulty = row.difficulty,
                type = row.type,
                dxScore = row.dxScore,
                maxDxScore = row.maxDxScore,
                fc = row.fc,
                fs = row.fs,
                isNew = isNew,
            ),
        )
    }
    val selected35 = b35.sortedByDescending { it.rating }.take(b35Count.coerceAtLeast(0))
    val selected15 = b15.sortedByDescending { it.rating }.take(b15Count.coerceAtLeast(0))
    return Best50State(
        total = (selected35 + selected15).sumOf { it.rating },
        b35 = selected35,
        b15 = selected15,
        latestVersion = latest,
        isEmpty = selected35.isEmpty() && selected15.isEmpty(),
    )
}
