package net.krtl.maimaid.core.domain

enum class ImportSource {
    DIVING_FISH,
    LXNS
}

data class ImportSummary(
    val source: ImportSource,
    val fetchedCount: Int,
    val upsertedCount: Int,
    val skippedCount: Int
)

