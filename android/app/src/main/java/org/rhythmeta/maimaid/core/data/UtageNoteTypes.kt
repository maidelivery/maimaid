package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UtageNoteTypes(
    val tap: Int,
    val hold: Int,
    val slide: Int,
    val touch: Int,
    @SerialName("break") val breakCount: Int,
)
