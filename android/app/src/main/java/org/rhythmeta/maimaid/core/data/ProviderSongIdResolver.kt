package org.rhythmeta.maimaid.core.data

internal object ProviderSongIdResolver {
    fun resolve(internalId: Int?, chartType: String): Int? {
        val id = internalId?.takeIf { it > 0 } ?: return null
        return if (chartType.equals("dx", ignoreCase = true) && id < 10_000) {
            id + 10_000
        } else {
            id
        }
    }

    fun relatedIds(songId: Int): List<Int> {
        if (songId <= 0) return emptyList()

        return buildList {
            fun addDistinct(id: Int) {
                if (id > 0 && id !in this) add(id)
            }

            addDistinct(songId)
            when {
                songId < 10_000 -> addDistinct(songId + 10_000)
                songId < 100_000 -> addDistinct(songId % 10_000)
                else -> {
                    val baseId = songId % 100_000
                    addDistinct(baseId)
                    if (baseId < 10_000) addDistinct(baseId + 10_000)
                }
            }
        }
    }
}
