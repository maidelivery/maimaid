package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaticBundleResponse(
    val version: String,
    val md5: String,
    val payload: Payload,
) {
    @Serializable
    data class Payload(
        val resources: Resources,
    )

    @Serializable
    data class Resources(
        @SerialName("data_json") val catalog: CatalogPayload,
        @SerialName("songid_json") val songIds: List<SongIdItem> = emptyList(),
        @SerialName("lxns_aliases") val aliases: AliasPayload? = null,
        @SerialName("chart_fit") val chartFit: ChartFitPayload? = null,
        @SerialName("df_chart_fit") val legacyChartFit: ChartFitPayload? = null,
        @SerialName("dan_info") val danInfo: List<DanCategory> = emptyList(),
    )

    @Serializable
    data class ChartFitPayload(
        val charts: Map<String, List<ChartFitStat>> = emptyMap(),
    )

    @Serializable
    data class ChartFitStat(
        val diff: String? = null,
        @SerialName("fit_diff") val fitDifficulty: Double? = null,
    )

    @Serializable
    data class SongIdItem(
        val id: Int,
        val name: String,
    )

    @Serializable
    data class AliasPayload(
        val aliases: List<AliasItem> = emptyList(),
    )

    @Serializable
    data class AliasItem(
        @SerialName("song_id") val songId: Int,
        val aliases: List<String> = emptyList(),
    )
}
