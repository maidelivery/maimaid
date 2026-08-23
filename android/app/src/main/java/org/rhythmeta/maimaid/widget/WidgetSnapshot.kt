package org.rhythmeta.maimaid.widget

import org.rhythmeta.maimaid.core.data.Best50State
import org.rhythmeta.maimaid.core.database.UserProfileEntity

data class WidgetScoreSummary(
    val title: String,
    val difficulty: String,
    val achievement: Double,
    val rating: Int,
    val imageName: String = "",
)

data class WidgetSnapshot(
    val profileId: String? = null,
    val profileName: String? = null,
    val server: String? = null,
    val avatarPath: String? = null,
    val avatarUrl: String? = null,
    val displayRating: Int = 0,
    val b35Rating: Int = 0,
    val b15Rating: Int = 0,
    val best50Count: Int = 0,
    val topScores: List<WidgetScoreSummary> = emptyList(),
    val updatedAt: Long = 0L,
    val accentArgb: Int = 0,
    val status: Status = Status.NoProfile,
) {
    enum class Status {
        NoProfile,
        NoScores,
        Ready,
    }
}

object WidgetSnapshotBuilder {
    fun build(
        profile: UserProfileEntity?,
        best50: Best50State,
        updatedAt: Long,
    ): WidgetSnapshot {
        if (profile == null) {
            return WidgetSnapshot(updatedAt = updatedAt)
        }

        val entries = (best50.b35 + best50.b15).sortedByDescending { it.rating }
        val b35Rating = best50.b35.sumOf { it.rating }
        val b15Rating = best50.b15.sumOf { it.rating }
        return WidgetSnapshot(
            profileId = profile.id,
            profileName = profile.name,
            server = profile.server,
            avatarPath = profile.avatarPath,
            avatarUrl = profile.avatarUrl,
            // Best Table's headline rating is calculated from the current B35/B15 set.
            displayRating = best50.total,
            b35Rating = b35Rating,
            b15Rating = b15Rating,
            best50Count = entries.size,
            topScores = entries.map {
                WidgetScoreSummary(
                    title = it.title,
                    difficulty = it.difficulty,
                    achievement = it.achievement,
                    rating = it.rating,
                    imageName = it.imageName,
                )
            },
            updatedAt = updatedAt,
            status = if (entries.isEmpty()) WidgetSnapshot.Status.NoScores else WidgetSnapshot.Status.Ready,
        )
    }
}
