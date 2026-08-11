package org.rhythmeta.maimaid.ui.util

import androidx.compose.ui.graphics.Color
import org.rhythmeta.maimaid.core.data.ScoreRules

object ScoreStatusColors {
    val FullCombo = Color(0xFF33BF33)
    val AllPerfect = Color(0xFFFF9900)
    val FullSync = Color(0xFF4D80FF)
    val FullSyncDx = Color(0xFFFFD700)

    private val RankSss = Color(0xFFFFD900)
    private val RankSs = Color(0xFFFFBF00)
    private val RankS = Color(0xFFFF9900)
    private val RankAaa = Color(0xFFCC99FF)
    private val RankAa = Color(0xFF99CCFF)
    private val RankA = Color(0xFF80E680)

    fun combo(value: String?): Color? = when (ScoreRules.canonicalFc(value)) {
        "fc", "fcp" -> FullCombo
        "ap", "app" -> AllPerfect
        else -> null
    }

    fun sync(value: String?): Color? = when (ScoreRules.canonicalFs(value)) {
        "sync", "fs", "fsp" -> FullSync
        "fsd", "fsdp" -> FullSyncDx
        else -> null
    }

    fun rank(value: String?): Color? = when (value?.trim()?.uppercase()) {
        "SSS+", "SSS" -> RankSss
        "SS+", "SS" -> RankSs
        "S+", "S" -> RankS
        "AAA" -> RankAaa
        "AA" -> RankAa
        "A" -> RankA
        else -> null
    }
}
