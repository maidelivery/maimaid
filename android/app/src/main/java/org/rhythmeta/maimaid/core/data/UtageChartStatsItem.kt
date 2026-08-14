package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class UtageChartStatsItem(
    val id: Int,
    val title: String,
    val notes: Int,
    val noteTypes: UtageNoteTypes? = null,
)
