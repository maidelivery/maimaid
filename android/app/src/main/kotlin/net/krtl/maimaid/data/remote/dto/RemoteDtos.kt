package net.krtl.maimaid.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AliasListResponse(
    val aliases: List<AliasItem> = emptyList()
)

@Serializable
data class VersionCheckResponse(
    val success: Boolean,
    val data: VersionCheckData? = null
)

@Serializable
data class VersionCheckData(
    val status: String,
    val description: String? = null
)

@Serializable
data class AliasItem(
    @SerialName("song_id") val songId: Int,
    val aliases: List<String> = emptyList()
)

@Serializable
data class SongIdItem(
    val id: Int,
    val name: String
)

@Serializable
data class RemoteDataResponse(
    val songs: List<RemoteSong> = emptyList(),
    val categories: List<RemoteCategory> = emptyList(),
    val versions: List<RemoteVersion> = emptyList()
)

@Serializable
data class RemoteVersion(
    val version: String,
    val abbr: String,
    val releaseDate: String? = null
)

@Serializable
data class RemoteCategory(
    val category: String
)

@Serializable
data class RemoteSong(
    val songId: String,
    val category: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val bpm: Double? = null,
    val imageName: String? = null,
    val version: String? = null,
    val releaseDate: String? = null,
    val isNew: Boolean? = null,
    val isLocked: Boolean? = null,
    val comment: String? = null,
    val sheets: List<RemoteSheet> = emptyList()
)

@Serializable
data class RemoteSheet(
    val type: String,
    val difficulty: String,
    val level: String,
    val levelValue: Double? = null,
    val internalLevel: String? = null,
    val internalLevelValue: Double? = null,
    val noteDesigner: String? = null,
    val noteCounts: RemoteNoteCounts? = null,
    val regions: Map<String, Boolean>? = null,
    val isSpecial: Boolean? = null
)

@Serializable
data class RemoteNoteCounts(
    val tap: Int? = null,
    val hold: Int? = null,
    val slide: Int? = null,
    val touch: Int? = null,
    @SerialName("break") val breakNote: Int? = null,
    val total: Int? = null
)

@Serializable
data class BackendStaticManifestResponse(
    val version: String,
    val md5: String,
    val createdAt: String? = null
)

@Serializable
data class BackendStaticBundleResponse(
    val version: String,
    val md5: String,
    val createdAt: String? = null,
    val payload: BackendStaticBundlePayload
)

@Serializable
data class BackendStaticBundlePayload(
    val resources: BackendStaticBundleResources = BackendStaticBundleResources()
)

@Serializable
data class BackendStaticBundleResources(
    @SerialName("data_json") val dataJson: RemoteDataResponse? = null,
    @SerialName("songid_json") val songIdJson: List<SongIdItem>? = null,
    @SerialName("lxns_aliases") val lxnsAliases: AliasListResponse? = null,
    @SerialName("chart_fit") val chartFit: ChartStatsResponse? = null,
    @SerialName("df_chart_fit") val legacyChartFit: ChartStatsResponse? = null,
    @SerialName("dan_info") val danInfo: JsonElement? = null
) {
    val resolvedChartFit: ChartStatsResponse?
        get() = chartFit ?: legacyChartFit
}

@Serializable
data class LxnsPresetIconListResponse(
    val icons: List<LxnsPresetIconDto> = emptyList()
)

@Serializable
data class LxnsPresetIconDto(
    val id: Int,
    val name: String,
    val description: String,
    val genre: String
)

@Serializable
data class ChartStatsResponse(
    val charts: Map<String, List<ChartStatDto>> = emptyMap()
)

@Serializable
data class ChartStatDto(
    val cnt: Double? = null,
    val diff: String? = null,
    @SerialName("fit_diff") val fitDiff: Double? = null,
    val avg: Double? = null,
    @SerialName("avg_dx") val avgDx: Double? = null,
    @SerialName("std_dev") val stdDev: Double? = null
)
