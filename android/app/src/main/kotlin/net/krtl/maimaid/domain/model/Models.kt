package net.krtl.maimaid.domain.model

enum class GameServer(val value: String) {
    JP("jp"),
    INTL("intl"),
    CN("cn");

    val displayName: String
        get() = when (this) {
            JP -> "Japan"
            INTL -> "International"
            CN -> "China"
        }

    companion object {
        fun fromValue(value: String?): GameServer = entries.firstOrNull { it.value == value?.lowercase() } ?: JP
    }
}

enum class ThemeMode(val rawValue: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromRawValue(rawValue: Int): ThemeMode = entries.firstOrNull { it.rawValue == rawValue } ?: SYSTEM
    }
}

enum class SyncStage {
    IDLE,
    FETCHING_REMOTE_DATA,
    FETCHING_ALIASES,
    FETCHING_ICONS,
    FETCHING_CHART_STATS,
    PROCESSING_SONGS,
    SAVING,
    COMPLETED,
    FAILED
}

data class Song(
    val songIdentifier: String,
    val category: String,
    val title: String,
    val artist: String,
    val imageName: String,
    val version: String?,
    val releaseDate: String?,
    val sortOrder: Int,
    val bpm: Double?,
    val isNew: Boolean,
    val isLocked: Boolean,
    val comment: String?,
    val searchKeywords: String?,
    val aliases: List<String>,
    val songId: Int,
    val isFavorite: Boolean,
    val sheets: List<Sheet>
)

data class Sheet(
    val sheetId: String,
    val songIdentifier: String,
    val type: String,
    val difficulty: String,
    val level: String,
    val levelValue: Double?,
    val internalLevel: String?,
    val internalLevelValue: Double?,
    val noteDesigner: String?,
    val tap: Int?,
    val hold: Int?,
    val slide: Int?,
    val touch: Int?,
    val breakCount: Int?,
    val total: Int?,
    val regionJp: Boolean,
    val regionIntl: Boolean,
    val regionUsa: Boolean,
    val regionCn: Boolean,
    val songId: Int
)

data class Score(
    val scoreKey: String,
    val sheetId: String,
    val userProfileId: String,
    val rate: Double,
    val rank: String,
    val achievementDate: Long,
    val dxScore: Int,
    val fc: String?,
    val fs: String?
)

data class PlayRecord(
    val id: String,
    val sheetId: String,
    val userProfileId: String,
    val rate: Double,
    val rank: String,
    val playDate: Long,
    val dxScore: Int,
    val fc: String?,
    val fs: String?
)

data class UserProfile(
    val id: String,
    val name: String,
    val server: GameServer,
    val avatarUrl: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val playerRating: Int,
    val plate: String?,
    val b35Count: Int,
    val b15Count: Int,
    val b35RecLimit: Int,
    val b15RecLimit: Int
)

data class SyncConfig(
    val id: Int = 1,
    val isAutoUploadEnabled: Boolean = false,
    val backgroundSyncInterval: Int = 0,
    val cloudBackupInterval: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastStaticDataUpdateDate: Long? = null,
    val lastCloudBackupDate: Long? = null,
    val lastSyncRevision: String = "0",
    val localDataOwnerUserId: String? = null,
    val pendingResolutionForUserId: String? = null,
    val pendingResolutionDetectedAt: Long? = null
)

data class MaimaiIcon(
    val id: Int,
    val name: String,
    val descriptionText: String,
    val genre: String
)

data class StaticSyncOptions(
    val updateRemoteData: Boolean = true,
    val updateAliases: Boolean = true,
    val updateCovers: Boolean = true,
    val updateIcons: Boolean = true,
    val updateDanData: Boolean = true,
    val updateChartStats: Boolean = true
)

data class StaticSyncStatus(
    val isSyncing: Boolean = false,
    val stage: SyncStage = SyncStage.IDLE,
    val progress: Double = 0.0,
    val message: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val downloadSpeedBytesPerSecond: Double = 0.0,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val useFitDiff: Boolean = false,
    val showScannerBoundingBox: Boolean = false,
    val syncOptions: StaticSyncOptions = StaticSyncOptions(),
    val hideDeletedSongs: Boolean = true,
    val versionSequence: List<String> = emptyList(),
    val categorySequence: List<String> = emptyList(),
    val versionsJson: String? = null,
    val chartStatsJson: String? = null,
    val didPerformInitialSync: Boolean = false
)

data class RatingEntry(
    val sheetId: String,
    val songId: Int,
    val songIdentifier: String,
    val songTitle: String,
    val imageName: String?,
    val achievement: Double,
    val rating: Int,
    val level: Double,
    val diff: String,
    val type: String,
    val dxScore: Int = 0,
    val maxDxScore: Int = 0,
    val fc: String? = null,
    val fs: String? = null
)

data class B50Result(
    val total: Int,
    val b35: List<RatingEntry>,
    val b15: List<RatingEntry>
)

enum class PlateType(val displayName: String) {
    KIWAMI("极"),
    SHO("将"),
    SHIN("神"),
    MAIMAI("舞舞")
}

data class PlateProgressItem(
    val version: String,
    val plateType: PlateType,
    val totalSheets: Int,
    val completedSheets: Int
)

data class RecommendationItem(
    val song: Song,
    val sheet: Sheet,
    val fitDiff: Double?,
    val diffGap: Double?,
    val currentRate: Double?,
    val potentialRating: Int,
    val potentialGain: Int,
    val targetRank: String,
    val targetAchievement: Double,
    val isNew: Boolean,
    val comprehensiveScore: Double
)

data class RecommendationResult(
    val b15: List<RecommendationItem>,
    val b35: List<RecommendationItem>
)

data class SongStatistics(
    val totalSongs: Int = 0,
    val totalSheets: Int = 0,
    val totalIcons: Int = 0,
    val totalCategories: Int = 0,
    val totalVersions: Int = 0
)

data class HomeSummary(
    val activeProfile: UserProfile?,
    val totalSongs: Int,
    val totalScores: Int,
    val b50: B50Result,
    val randomSong: Song?
)

enum class CommunityAliasSubmitStatus {
    CREATED,
    REJECTED_DUPLICATE,
    QUOTA_EXCEEDED,
    UNAUTHENTICATED,
    INVALID_REQUEST,
    ERROR
}

enum class CommunityAliasDuplicateReason {
    LXNS_EXISTING,
    COMMUNITY_EXISTING,
    ADMIN_REJECTED_LOCKED
}

data class CommunityAliasSubmitCandidate(
    val id: String,
    val songIdentifier: String,
    val aliasText: String,
    val status: String,
    val createdAt: Long
)

data class CommunityAliasExistingCandidate(
    val candidateId: String,
    val aliasText: String,
    val status: String,
    val similarity: Double,
    val bucket: String,
    val supportCount: Int,
    val opposeCount: Int
)

data class CommunityAliasSubmitResponse(
    val status: CommunityAliasSubmitStatus,
    val message: String,
    val duplicateReason: CommunityAliasDuplicateReason? = null,
    val candidate: CommunityAliasSubmitCandidate? = null,
    val existingCandidates: List<CommunityAliasExistingCandidate> = emptyList(),
    val similarAliases: List<String> = emptyList(),
    val quotaRemaining: Int? = null
)

data class CommunityAliasVotingBoardItem(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val submitterId: String,
    val voteOpenAt: Long?,
    val voteCloseAt: Long?,
    val supportCount: Int,
    val opposeCount: Int,
    val myVote: Int?,
    val createdAt: Long
)

data class CommunityAliasMyCandidate(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val status: String,
    val voteOpenAt: Long?,
    val voteCloseAt: Long?,
    val supportCount: Int,
    val opposeCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class CommunityAliasVoteResult(
    val candidateId: String,
    val supportCount: Int,
    val opposeCount: Int,
    val myVote: Int?
)

data class CommunityAliasApprovedAlias(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val updatedAt: Long,
    val approvedAt: Long?
)
