package net.krtl.maimaid.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import net.krtl.maimaid.BuildConfig
import net.krtl.maimaid.core.data.remote.BackendConfig
import net.krtl.maimaid.data.assets.CoverArtStore
import net.krtl.maimaid.data.assets.DanCatalogStore
import net.krtl.maimaid.data.local.dao.MaimaiDao
import net.krtl.maimaid.data.local.entity.MaimaiIconEntity
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SongEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity
import net.krtl.maimaid.data.remote.api.StaticDataApi
import net.krtl.maimaid.data.remote.dto.BackendStaticBundleResponse
import net.krtl.maimaid.data.remote.dto.BackendStaticManifestResponse
import net.krtl.maimaid.data.remote.dto.LxnsPresetIconListResponse
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.B50Result
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.HomeSummary
import net.krtl.maimaid.domain.model.MaimaiIcon
import net.krtl.maimaid.domain.model.PlateProgressItem
import net.krtl.maimaid.domain.model.PlayRecord
import net.krtl.maimaid.domain.model.RecommendationResult
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.SongStatistics
import net.krtl.maimaid.domain.model.StaticSyncOptions
import net.krtl.maimaid.domain.model.StaticSyncStatus
import net.krtl.maimaid.domain.model.SyncConfig
import net.krtl.maimaid.domain.model.SyncStage
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.repository.PreferencesRepository
import net.krtl.maimaid.domain.repository.ProfileRepository
import net.krtl.maimaid.domain.repository.RecommendationRepository
import net.krtl.maimaid.domain.repository.ScoreRepository
import net.krtl.maimaid.domain.repository.StaticDataRepository
import net.krtl.maimaid.domain.usecase.RatingEngine
import net.krtl.maimaid.util.asDomain
import net.krtl.maimaid.util.asEntity
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class StaticDataRepositoryImpl(
    private val context: Context,
    private val dao: MaimaiDao,
    private val preferencesRepository: PreferencesRepository,
    private val staticDataApi: StaticDataApi,
    private val json: Json,
    private val okHttpClient: OkHttpClient
) : StaticDataRepository {
    private companion object {
        const val DAN_GALLERY_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/gallery.yaml"
        const val LXNS_ICON_LIST_URL = "https://maimai.lxns.net/api/v0/maimai/icon/list"
        const val STATIC_SYNC_PREFS = "maimaid.static.sync"
        const val KEY_STATIC_BUNDLE_MD5 = "staticBundleMd5"
    }

    private val mutableSyncStatus = MutableStateFlow(StaticSyncStatus())
    private val syncMutex = Mutex()
    private val staticSyncPrefs by lazy {
        context.getSharedPreferences(STATIC_SYNC_PREFS, Context.MODE_PRIVATE)
    }
    override val syncStatus: Flow<StaticSyncStatus> = mutableSyncStatus

    override fun observeSongs(): Flow<List<Song>> =
        dao.observeSongsWithSheets().map { list -> list.map { it.asDomain() } }

    override fun observeSong(songIdentifier: String): Flow<Song?> =
        dao.observeSongWithSheets(songIdentifier).map { it?.asDomain() }

    override suspend fun setSongFavorite(songIdentifier: String, isFavorite: Boolean) {
        dao.updateSongFavorite(songIdentifier, isFavorite)
    }

    override fun observeSyncConfig(): Flow<SyncConfig> = dao.observeSyncConfig().map {
        it?.asDomain() ?: SyncConfigEntity(
            isAutoUploadEnabled = false,
            backgroundSyncInterval = 0,
            themeRawValue = 0,
            lastStaticDataUpdateDate = null,
        ).asDomain()
    }

    override fun observeIcons(): Flow<List<MaimaiIcon>> =
        dao.observeIcons().map { list -> list.map { it.asDomain() } }

    override suspend fun syncStaticData(options: StaticSyncOptions) {
        val effectiveOptions = if (BuildConfig.DEBUG) options else StaticSyncOptions()
        if (syncMutex.isLocked) return
        syncMutex.withLock {
            logStatus(SyncStage.FETCHING_REMOTE_DATA, 0.05, "Sync started")
            try {
                val currentSongs = dao.getSongsWithSheets()
                val bundleResult = runCatching { fetchBackendStaticBundleIfNeeded() }
                    .onFailure { error ->
                        appendLog("Static bundle fetch skipped: ${error.message}")
                    }
                    .getOrNull()
                if (bundleResult?.isUpToDate == true) {
                    logStatus(SyncStage.COMPLETED, 1.0, "No static data update available.")
                    return@withLock
                }
                val bundleResources = bundleResult?.bundle?.payload?.resources

                val remoteData = if (effectiveOptions.updateRemoteData) {
                    bundleResources?.dataJson ?: staticDataApi.getRemoteData()
                } else {
                    null
                }

                val aliasMap = mutableMapOf<String, List<String>>()
                val titleToSongId = mutableMapOf<String, Int>()
                val providerIds = mutableMapOf<String, List<Int>>()
                var icons = emptyList<MaimaiIconEntity>()

                if (remoteData != null) {
                    preferencesRepository.setVersionMetadata(
                        versionsJson = json.encodeToString(remoteData.versions),
                        versionSequence = remoteData.versions.map { it.version },
                        categorySequence = remoteData.categories.map { it.category }
                    )
                    remoteData.songs.forEach { song ->
                        val title = song.title?.trim().orEmpty()
                        val id = song.songId.toIntOrNull()
                        if (title.isNotEmpty() && id != null && titleToSongId[title] == null) {
                            titleToSongId[title] = id
                        }
                    }
                }

                if (effectiveOptions.updateAliases) {
                    logStatus(SyncStage.FETCHING_ALIASES, 0.15, "Fetching aliases")
                    val providerIdResponse = bundleResources?.songIdJson ?: runCatching {
                        staticDataApi.getProviderIds()
                    }.onFailure { error ->
                        appendLog("Provider ID lookup skipped: ${error.message}")
                    }.getOrDefault(emptyList())
                    providerIdResponse.groupBy { it.name.trim().ifEmpty { it.name } }
                        .forEach { (name, ids) ->
                            providerIds[name] = ids.map { it.id }
                            if (titleToSongId[name] == null) {
                                titleToSongId[name] = ids.firstOrNull()?.id ?: 0
                            }
                        }

                    val songIdToTitle = remoteData?.songs
                        ?.mapNotNull { song ->
                            val title = song.title?.trim().orEmpty()
                            val songId = song.songId.toIntOrNull()
                            if (title.isEmpty() || songId == null) null else songId to title
                        }
                        ?.toMap()
                        .orEmpty()
                    val aliasItems = bundleResources?.lxnsAliases?.aliases.orEmpty()
                    aliasItems.forEach { item ->
                        val title = songIdToTitle[item.songId] ?: return@forEach
                        val existing = aliasMap[title].orEmpty().toMutableList()
                        val seen = existing.map { it.trim().lowercase() }.toMutableSet()
                        item.aliases.forEach { alias ->
                            val normalized = alias.trim()
                            if (normalized.isEmpty()) return@forEach
                            if (seen.add(normalized.lowercase())) {
                                existing += normalized
                            }
                        }
                        aliasMap[title] = existing
                    }
                }

                if (effectiveOptions.updateIcons) {
                    logStatus(SyncStage.FETCHING_ICONS, 0.22, "Fetching icons")
                    runCatching {
                        val payload = json.decodeFromString<LxnsPresetIconListResponse>(
                            downloadText(LXNS_ICON_LIST_URL)
                        )
                        icons = payload.icons.map {
                            MaimaiIconEntity(
                                id = it.id,
                                name = it.name,
                                descriptionText = it.description,
                                genre = it.genre
                            )
                        }
                    }.onFailure { error ->
                        appendLog("Icon fetch skipped: ${error.message}")
                    }
                }

                if (effectiveOptions.updateDanData) {
                    logStatus(SyncStage.FETCHING_REMOTE_DATA, 0.30, "Fetching Dan data")
                    val bundledDan = bundleResources?.danInfo
                    if (bundledDan != null) {
                        DanCatalogStore.replaceFromJson(context, bundledDan.toString())
                    } else {
                        val yamlString = downloadText(DAN_GALLERY_URL)
                        DanCatalogStore.replaceFromYaml(context, yamlString)
                    }
                }

                if (effectiveOptions.updateChartStats) {
                    logStatus(SyncStage.FETCHING_CHART_STATS, 0.40, "Updating chart stats")
                    bundleResources?.resolvedChartFit?.let { chartStats ->
                        preferencesRepository.setChartStatsJson(json.encodeToString(chartStats))
                    }
                }

                logStatus(SyncStage.PROCESSING_SONGS, 0.50, "Processing songs")
                val communityAliasMap = dao.getCommunityAliasCacheRows()
                    .groupBy { it.songIdentifier }
                    .mapValues { entry -> entry.value.map { it.aliasText } }
                val songEntities = mutableListOf<SongEntity>()
                val sheetEntities = mutableListOf<SheetEntity>()
                val sourceSongs = remoteData?.songs?.map { remote ->
                    remote.songId to remote
                } ?: currentSongs.map { it.song.songIdentifier to it }

                sourceSongs.forEachIndexed { index, (songIdentifier, source) ->
                    val isRemote = source is net.krtl.maimaid.data.remote.dto.RemoteSong
                    val title = if (isRemote) {
                        source.title?.trim().orEmpty()
                    } else {
                        (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.title
                    }
                    val artist = if (isRemote) {
                        source.artist.orEmpty()
                    } else {
                        (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.artist
                    }
                    val songTitle = title.ifBlank {
                        if (isRemote) {
                            source.title.orEmpty()
                        } else {
                            (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.title
                        }
                    }
                    val existing =
                        currentSongs.firstOrNull { it.song.songIdentifier == songIdentifier }?.song
                    val possibleIds = providerIds[songTitle]
                    val sheetSource = if (isRemote) {
                        source.sheets.map { Triple(it.type, it.difficulty, it) }
                    } else {
                        (source as net.krtl.maimaid.data.local.relation.SongWithSheets).sheets.map {
                            Triple(it.type, it.difficulty, it)
                        }
                    }

                    val assignedSongId = titleToSongId[songTitle]
                        ?: possibleIds?.firstOrNull { id ->
                            sheetSource.any { (type, _, _) ->
                                type.equals(
                                    "utage",
                                    true
                                ) && id >= 100000
                            }
                        }
                        ?: possibleIds?.firstOrNull { id ->
                            sheetSource.any { (type, _, _) ->
                                type.equals(
                                    "dx",
                                    true
                                ) && id in 10000..99999
                            }
                        }
                        ?: possibleIds?.firstOrNull { id ->
                            sheetSource.any { (type, _, _) ->
                                type.equals(
                                    "std",
                                    true
                                ) && id < 10000
                            }
                        }
                        ?: existing?.songId
                        ?: 0

                    val aliases = mergeAliasLists(
                        aliasMap[songTitle] ?: existing?.aliases ?: emptyList(),
                        communityAliasMap[songIdentifier].orEmpty()
                    )
                    val category = if (isRemote) {
                        source.category.orEmpty()
                    } else {
                        (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.category
                    }
                    val imageName = if (isRemote) {
                        source.imageName?.trim().orEmpty()
                    } else {
                        (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.imageName
                    }
                    val version =
                        if (isRemote) source.version else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.version
                    val releaseDate =
                        if (isRemote) source.releaseDate else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.releaseDate
                    val bpm =
                        if (isRemote) source.bpm else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.bpm
                    val isNew = if (isRemote) source.isNew
                        ?: false else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.isNew
                    val isLocked = if (isRemote) source.isLocked
                        ?: false else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.isLocked
                    val comment =
                        if (isRemote) source.comment else (source as net.krtl.maimaid.data.local.relation.SongWithSheets).song.comment
                    songEntities += SongEntity(
                        songIdentifier = songIdentifier,
                        category = category,
                        title = songTitle,
                        artist = artist,
                        imageName = imageName,
                        version = version,
                        releaseDate = releaseDate,
                        sortOrder = index,
                        bpm = bpm,
                        isNew = isNew,
                        isLocked = isLocked,
                        comment = comment,
                        searchKeywords = buildString {
                            append(songTitle)
                            append(' ')
                            append(artist)
                            if (aliases.isNotEmpty()) append(' ').append(aliases.joinToString(" "))
                            if (assignedSongId > 0) append(' ').append(assignedSongId)
                        },
                        aliases = aliases,
                        songId = assignedSongId,
                        isFavorite = existing?.isFavorite ?: false
                    )

                    sheetSource.forEach { (type, difficulty, rawSheet) ->
                        val sheetId = "${songIdentifier}_${type}_${difficulty}"
                        if (rawSheet is net.krtl.maimaid.data.remote.dto.RemoteSheet) {
                            val providerSheetId = possibleIds?.firstOrNull { id ->
                                when {
                                    type.equals("utage", true) -> id >= 100000
                                    type.equals("dx", true) -> id in 10000..99999
                                    else -> id < 10000
                                }
                            } ?: assignedSongId
                            sheetEntities += SheetEntity(
                                sheetId = sheetId,
                                songIdentifier = songIdentifier,
                                type = type,
                                difficulty = difficulty,
                                level = rawSheet.level,
                                levelValue = rawSheet.levelValue,
                                internalLevel = rawSheet.internalLevel,
                                internalLevelValue = rawSheet.internalLevelValue,
                                noteDesigner = rawSheet.noteDesigner,
                                tap = rawSheet.noteCounts?.tap,
                                hold = rawSheet.noteCounts?.hold,
                                slide = rawSheet.noteCounts?.slide,
                                touch = rawSheet.noteCounts?.touch,
                                breakCount = rawSheet.noteCounts?.breakNote,
                                total = rawSheet.noteCounts?.total,
                                regionJp = rawSheet.regions?.get("jp") ?: false,
                                regionIntl = rawSheet.regions?.get("intl") ?: false,
                                regionUsa = rawSheet.regions?.get("usa") ?: false,
                                regionCn = rawSheet.regions?.get("cn") ?: false,
                                songId = providerSheetId
                            )
                        } else if (rawSheet is SheetEntity) {
                            sheetEntities += rawSheet
                        }
                    }
                }

                if (remoteData != null) {
                    if (songEntities.isEmpty()) {
                        dao.clearSongs()
                    } else {
                        dao.deleteSongsNotIn(songEntities.map { it.songIdentifier })
                    }
                }
                dao.upsertSongs(songEntities)
                dao.upsertSheets(sheetEntities)
                if (remoteData != null) {
                    cleanupRemoteSheetsAndRelatedData(
                        dao = dao,
                        songs = songEntities,
                        sheets = sheetEntities
                    )
                }
                if (icons.isNotEmpty()) {
                    dao.upsertIcons(icons)
                }
                if (effectiveOptions.updateCovers) {
                    val imageNames = songEntities.map { it.imageName }
                    logStatus(SyncStage.SAVING, 0.78, "Downloading cover art")
                    val coverDownloadStartedAt = System.currentTimeMillis()
                    CoverArtStore.prefetchMissingCovers(
                        context = context,
                        okHttpClient = okHttpClient,
                        imageNames = imageNames
                    ) { completed, total, downloadedBytes, totalBytes ->
                        val elapsedSeconds =
                            ((System.currentTimeMillis() - coverDownloadStartedAt).coerceAtLeast(1L)) / 1000.0
                        val speedBytesPerSecond = downloadedBytes / elapsedSeconds
                        mutableSyncStatus.update {
                            it.copy(
                                isSyncing = true,
                                stage = SyncStage.SAVING,
                                progress = 0.78 + (completed.toDouble() / total.toDouble()) * 0.18,
                                message = "Downloading cover art ($completed/$total)",
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes.takeIf { bytes -> bytes > 0L },
                                downloadSpeedBytesPerSecond = speedBytesPerSecond,
                                errorMessage = null
                            )
                        }
                    }
                }

                val currentConfig = dao.getSyncConfig() ?: SyncConfigEntity(
                    isAutoUploadEnabled = false,
                    backgroundSyncInterval = 0,
                    themeRawValue = preferencesRepository.preferences.first().themeMode.rawValue,
                    lastStaticDataUpdateDate = null,
                )
                logStatus(SyncStage.SAVING, 0.97, "Saving local data")
                dao.upsertSyncConfig(currentConfig.copy(lastStaticDataUpdateDate = System.currentTimeMillis()))
                bundleResult?.bundle?.md5?.let(::saveStaticBundleMd5)
                preferencesRepository.markInitialSyncComplete()
                logStatus(SyncStage.COMPLETED, 1.0, "Static data updated")
            } catch (error: Throwable) {
                mutableSyncStatus.update {
                    it.copy(
                        isSyncing = false,
                        stage = SyncStage.FAILED,
                        progress = 0.0,
                        message = "Sync failed",
                        downloadedBytes = 0L,
                        totalBytes = null,
                        downloadSpeedBytesPerSecond = 0.0,
                        errorMessage = error.message ?: "Unknown error",
                        logs = it.logs + "Error: ${error.message}"
                    )
                }
                throw error
            }
        }
    }

    override suspend fun updateSyncConfig(transform: (SyncConfig) -> SyncConfig) {
        val current = dao.getSyncConfig()?.asDomain() ?: SyncConfig()
        dao.upsertSyncConfig(transform(current).asEntity())
    }

    override suspend fun getSongStatistics(): SongStatistics {
        val prefs = preferencesRepository.preferences.first()
        return SongStatistics(
            totalSongs = dao.getSongCount(),
            totalSheets = dao.getSheetCount(),
            totalIcons = dao.getIconCount(),
            totalCategories = prefs.categorySequence.size,
            totalVersions = prefs.versionSequence.size
        )
    }

    private fun logStatus(stage: SyncStage, progress: Double, message: String) {
        mutableSyncStatus.update {
            it.copy(
                isSyncing = stage != SyncStage.COMPLETED,
                stage = stage,
                progress = progress,
                message = message,
                downloadedBytes = 0L,
                totalBytes = null,
                downloadSpeedBytesPerSecond = 0.0,
                logs = it.logs + message,
                errorMessage = null
            )
        }
    }

    private fun appendLog(message: String) {
        mutableSyncStatus.update { state ->
            state.copy(logs = state.logs + message)
        }
    }

    private data class BackendBundleFetchResult(
        val bundle: BackendStaticBundleResponse? = null,
        val isUpToDate: Boolean = false
    )

    private suspend fun fetchBackendStaticBundleIfNeeded(): BackendBundleFetchResult? {
        val manifestUrl = BackendConfig.endpoint("v1/static/manifest") ?: return null
        val manifest = requestJson(
            url = manifestUrl,
            deserializer = BackendStaticManifestResponse.serializer()
        ) ?: return null

        val localMd5 = staticSyncPrefs.getString(KEY_STATIC_BUNDLE_MD5, null)
        if (!localMd5.isNullOrBlank() && localMd5 == manifest.md5) {
            return BackendBundleFetchResult(isUpToDate = true)
        }

        val encodedVersion = Uri.encode(manifest.version)
        val bundleUrl = BackendConfig.endpoint("v1/static/bundle/$encodedVersion") ?: return null
        val bundle = requestJson(
            url = bundleUrl,
            deserializer = BackendStaticBundleResponse.serializer()
        ) ?: return null
        return BackendBundleFetchResult(bundle = bundle, isUpToDate = false)
    }

    private suspend fun <T> requestJson(
        url: HttpUrl,
        deserializer: DeserializationStrategy<T>
    ): T? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("X-Maimaid-Client", "app")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext null
            }
            val body = response.body.string()
            return@withContext runCatching {
                json.decodeFromString(deserializer, body)
            }.getOrNull()
        }
    }

    private fun saveStaticBundleMd5(md5: String) {
        staticSyncPrefs.edit { putString(KEY_STATIC_BUNDLE_MD5, md5) }
    }

    private suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Request failed: ${response.code}" }
            response.body.string()
        }
    }
}

class ProfileRepositoryImpl(
    private val dao: MaimaiDao
) : ProfileRepository {
    private val profileMutex = Mutex()

    override fun observeProfiles(): Flow<List<UserProfile>> =
        dao.observeProfiles().map { list -> list.map { it.asDomain() } }

    override fun observeActiveProfile(): Flow<UserProfile?> =
        dao.observeActiveProfile().map { it?.asDomain() }

    override suspend fun ensureActiveProfile(): UserProfile = profileMutex.withLock {
        dao.getActiveProfile()?.let { return@withLock it.asDomain() }
        val first = dao.getProfiles().firstOrNull()
        if (first != null) {
            dao.clearActiveProfiles()
            dao.activateProfile(first.id)
            first.copy(isActive = true).asDomain()
        } else {
            val profile = UserProfileEntity(
                id = UUID.randomUUID().toString(),
                name = "Player",
                server = GameServer.JP.value,
                avatarUrl = null,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                playerRating = 0,
                plate = null,
                b35Count = 35,
                b15Count = 15,
                b35RecLimit = 10,
                b15RecLimit = 10
            )
            dao.upsertProfile(profile)
            profile.asDomain()
        }
    }

    override suspend fun setActiveProfile(profileId: String) {
        dao.clearActiveProfiles()
        dao.activateProfile(profileId)
    }

    override suspend fun saveProfile(profile: UserProfile) {
        if (profile.isActive) dao.clearActiveProfiles()
        dao.upsertProfile(profile.asEntity())
        if (profile.isActive) dao.activateProfile(profile.id)
    }

    override suspend fun deleteProfile(profileId: String) {
        val activeId = dao.getActiveProfile()?.id
        dao.deletePlayRecordsForUser(profileId)
        dao.deleteScoresForUser(profileId)
        dao.deleteProfile(profileId)
        if (activeId == profileId) ensureActiveProfile()
    }
}

class ScoreRepositoryImpl(
    private val dao: MaimaiDao
) : ScoreRepository {
    override fun observeScores(userProfileId: String): Flow<List<Score>> =
        dao.observeScores(userProfileId).map { list -> list.map { it.asDomain() } }

    override fun observePlayRecords(userProfileId: String): Flow<List<PlayRecord>> =
        dao.observePlayRecords(userProfileId).map { list -> list.map { it.asDomain() } }

    override suspend fun getScores(userProfileId: String): List<Score> =
        dao.getScores(userProfileId).map { it.asDomain() }

    override suspend fun getPlayRecords(userProfileId: String): List<PlayRecord> =
        dao.getPlayRecords(userProfileId).map { it.asDomain() }

    override suspend fun getScore(sheetId: String, userProfileId: String): Score? =
        dao.getScore(sheetId, userProfileId)?.asDomain()

    override suspend fun saveScore(
        sheetId: String,
        userProfileId: String,
        rate: Double,
        dxScore: Int,
        fc: String?,
        fs: String?
    ): Score {
        val existing = dao.getScore(sheetId, userProfileId)
        val entity = existing?.copy(
            rate = maxOf(existing.rate, rate),
            rank = RatingEngine.calculateRank(maxOf(existing.rate, rate)),
            achievementDate = System.currentTimeMillis(),
            dxScore = maxOf(existing.dxScore, dxScore),
            fc = RatingEngine.bestFc(existing.fc, fc),
            fs = RatingEngine.bestFs(existing.fs, fs)
        ) ?: run {
            ScoreEntity(
                scoreKey = "$userProfileId::$sheetId",
                sheetId = sheetId,
                userProfileId = userProfileId,
                rate = rate,
                rank = RatingEngine.calculateRank(rate),
                achievementDate = System.currentTimeMillis(),
                dxScore = dxScore,
                fc = fc,
                fs = fs
            )
        }
        dao.upsertScore(entity)
        dao.insertPlayRecord(
            PlayRecordEntity(
                id = UUID.randomUUID().toString(),
                sheetId = sheetId,
                userProfileId = userProfileId,
                rate = rate,
                rank = RatingEngine.calculateRank(rate),
                playDate = System.currentTimeMillis(),
                dxScore = dxScore,
                fc = fc,
                fs = fs
            )
        )
        return entity.asDomain()
    }
}

class RecommendationRepositoryImpl(
    private val dao: MaimaiDao,
    private val preferencesRepository: PreferencesRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository
) : RecommendationRepository {
    override suspend fun getB50(
        profile: UserProfile,
        songs: List<Song>,
        scores: List<Score>,
        preferences: AppPreferencesState
    ): B50Result =
        RatingEngine.calculateB50(
            profile,
            songs,
            scores,
            preferences.versionSequence,
            preferences.chartStatsJson,
            preferences.useFitDiff
        )

    override suspend fun getRecommendations(
        profile: UserProfile,
        songs: List<Song>,
        scores: List<Score>,
        preferences: AppPreferencesState
    ): RecommendationResult =
        RatingEngine.calculateRecommendations(
            profile,
            songs,
            scores,
            preferences.versionSequence,
            preferences.chartStatsJson
        )

    override suspend fun getPlateProgress(
        songs: List<Song>,
        scores: List<Score>
    ): List<PlateProgressItem> =
        RatingEngine.calculatePlateProgress(songs, scores)

    override suspend fun getHomeSummary(): HomeSummary {
        val profile = profileRepository.ensureActiveProfile()
        val songs = dao.getSongsWithSheets().map { it.asDomain() }
        val preferences = preferencesRepository.preferences.first()
        val scores = scoreRepository.getScores(profile.id)
        return HomeSummary(
            activeProfile = profile,
            totalSongs = songs.size,
            totalScores = scores.size,
            b50 = getB50(profile, songs, scores, preferences),
            randomSong = songs.filter { it.sheets.any { sheet -> sheet.regionJp || sheet.regionIntl || sheet.regionCn } }
                .shuffled().firstOrNull()
        )
    }
}

private fun mergeAliasLists(base: List<String>, additions: List<String>): List<String> {
    val merged = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    (base + additions).forEach { alias ->
        val normalized = alias.trim()
        if (normalized.isEmpty()) {
            return@forEach
        }
        if (seen.add(normalized.lowercase())) {
            merged += normalized
        }
    }
    return merged
}

internal suspend fun cleanupRemoteSheetsAndRelatedData(
    dao: MaimaiDao,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>
) {
    val sheetIdsBySong = sheets
        .groupBy(SheetEntity::songIdentifier)
        .mapValues { (_, group) -> group.map(SheetEntity::sheetId) }
    songs.forEach { song ->
        val validSheetIds = sheetIdsBySong[song.songIdentifier].orEmpty()
        if (validSheetIds.isEmpty()) {
            dao.deleteSheetsForSong(song.songIdentifier)
        } else {
            dao.deleteSheetsNotIn(song.songIdentifier, validSheetIds)
        }
    }

    val validSheetIds = sheets.map(SheetEntity::sheetId)
    val cleanupIds = validSheetIds.ifEmpty { listOf("__no_valid_sheet__") }
    dao.deleteScoresNotInSheets(cleanupIds)
    dao.deletePlayRecordsNotInSheets(cleanupIds)
}
