package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.rhythmeta.maimaid.core.database.CatalogDao
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongCategoryEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.network.StaticBundleClient
import java.text.Normalizer

class CatalogRepository(
    private val database: MaimaidDatabase,
    private val client: StaticBundleClient,
    private val syncStateStore: CatalogSyncStateStore,
    private val coverImageStore: CoverImageStore,
    private val presetAvatarRepository: PresetAvatarRepository,
    private val chartFitStore: ChartFitStore,
    private val danStore: DanStore,
) {
    private val catalogDao: CatalogDao = database.catalogDao()
    private val syncMutex = Mutex()
    private val mutableSyncStatus = MutableStateFlow<CatalogSyncStatus>(CatalogSyncStatus.Idle)
    private val mutableChartFit = MutableSharedFlow<StaticBundleResponse.ChartFitPayload>(replay = 1)
    private val mutableDanCategories = MutableSharedFlow<List<DanCategory>>(replay = 1)

    val syncStatus = mutableSyncStatus.asStateFlow()
    val songCount: Flow<Int> = catalogDao.observeSongCount()
    val sheetCount: Flow<Int> = catalogDao.observeSheetCount()
    val featuredSongs: Flow<List<SongEntity>> = catalogDao.observeFeaturedSongs(limit = 6)
    val songs: Flow<List<SongEntity>> = catalogDao.observeSongs()
    val sheets: Flow<List<SheetEntity>> = catalogDao.observeSheets()
    val aliases: Flow<List<SongAliasEntity>> = catalogDao.observeAliases()
    val versions: Flow<List<GameVersionEntity>> = catalogDao.observeVersions()
    val categories: Flow<List<SongCategoryEntity>> = catalogDao.observeCategories()
    val chartFit: Flow<StaticBundleResponse.ChartFitPayload> = mutableChartFit.onStart {
        emit(chartFitStore.load())
    }
    val danCategories: Flow<List<DanCategory>> = mutableDanCategories.onStart {
        emit(danStore.load())
    }

    fun observeSheetsForSong(songIdentifier: String): Flow<List<SheetEntity>> =
        catalogDao.observeSheetsForSong(songIdentifier)

    fun observeAliasesForSong(songIdentifier: String): Flow<List<String>> =
        catalogDao.observeAliasesForSong(songIdentifier)

    suspend fun fetchStaticManifest(): StaticManifest = client.fetchManifest()

    suspend fun currentStaticDataMd5(): String? = syncStateStore.currentMd5()

    // Existing installations may predate the sync metadata store, while a populated catalog
    // remains sufficient to enter the app and refresh in the background.
    suspend fun hasCompletedInitialSync(): Boolean = catalogDao.songCount() > 0

    suspend fun setFavorite(songIdentifier: String, isFavorite: Boolean) {
        catalogDao.setFavorite(songIdentifier, isFavorite)
    }

    suspend fun refresh(force: Boolean = false) = syncMutex.withLock {
        val localSongCount = catalogDao.songCount()
        mutableSyncStatus.value = CatalogSyncStatus.Checking
        try {
            val manifest = client.fetchManifest()
            val currentMd5 = syncStateStore.currentMd5()
            if (
                localSongCount > 0 &&
                currentMd5 == manifest.md5 &&
                syncStateStore.isCurrentSchema() &&
                chartFitStore.hasCache() &&
                danStore.hasCache() &&
                !force
            ) {
                downloadMissingCovers(
                    imageNames = catalogDao.imageNames(),
                    assets = manifest.assets,
                    reporter = StageProgressReporter(
                        version = manifest.version,
                        stage = CatalogSyncStage.Covers,
                        overallStart = 0f,
                        overallEnd = 0.72f,
                    ),
                )
                syncPresetAvatars(
                    assets = manifest.assets,
                    reporter = StageProgressReporter(
                        version = manifest.version,
                        stage = CatalogSyncStage.PresetAvatars,
                        overallStart = 0.72f,
                        overallEnd = 0.98f,
                    ),
                )
                showSimpleStage(manifest.version, CatalogSyncStage.Finalizing, 0.99f)
                mutableSyncStatus.value = CatalogSyncStatus.Ready(manifest.version, fromCache = true)
                return@withLock
            }

            val bundleReporter = StageProgressReporter(
                version = manifest.version,
                stage = CatalogSyncStage.CatalogBundle,
                overallStart = 0f,
                overallEnd = 0.32f,
                useByteProgress = true,
            )
            bundleReporter.start(totalItems = 1)
            val bundle = client.fetchBundle(manifest, bundleReporter::onTransfer)
            bundleReporter.complete()
            showSimpleStage(bundle.version, CatalogSyncStage.ImportingCatalog, 0.4f)
            applyBundle(bundle)
            downloadMissingCovers(
                imageNames = bundle.payload.resources.catalog.songs.map { it.imageName.orEmpty() },
                assets = manifest.assets,
                reporter = StageProgressReporter(
                    version = bundle.version,
                    stage = CatalogSyncStage.Covers,
                    overallStart = 0.44f,
                    overallEnd = 0.78f,
                ),
            )
            syncPresetAvatars(
                bundledAvatars = bundle.payload.resources.presetAvatars?.icons,
                assets = manifest.assets,
                reporter = StageProgressReporter(
                    version = bundle.version,
                    stage = CatalogSyncStage.PresetAvatars,
                    overallStart = 0.78f,
                    overallEnd = 0.98f,
                ),
            )
            showSimpleStage(bundle.version, CatalogSyncStage.Finalizing, 0.99f)
            syncStateStore.save(bundle.version, bundle.md5)
            mutableSyncStatus.value = CatalogSyncStatus.Ready(bundle.version, fromCache = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableSyncStatus.value = CatalogSyncStatus.Failed(
                message = error.message ?: error::class.java.simpleName,
                hasLocalCatalog = localSongCount > 0,
            )
        }
    }

    private suspend fun downloadMissingCovers(
        imageNames: Iterable<String>,
        assets: StaticAssetConfiguration?,
        reporter: StageProgressReporter,
    ) {
        reporter.start()
        coverImageStore.downloadMissing(imageNames, reporter::onItemsProgress) { imageName, destination ->
            client.downloadCover(imageName, destination, assets, reporter::onTransfer)
        }
        reporter.complete()
    }

    private suspend fun syncPresetAvatars(
        bundledAvatars: List<PresetAvatar>? = null,
        assets: StaticAssetConfiguration?,
        reporter: StageProgressReporter,
    ) {
        reporter.start()
        val avatars = try {
            presetAvatarRepository.refresh(bundledAvatars)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            reporter.complete()
            return
        }
        presetAvatarRepository.downloadMissing(avatars, reporter::onItemsProgress) { id, destination ->
            client.downloadPresetAvatar(id, destination, assets, reporter::onTransfer)
        }
        reporter.complete()
    }

    private fun showSimpleStage(version: String, stage: CatalogSyncStage, fraction: Float) {
        mutableSyncStatus.value = CatalogSyncStatus.Downloading(
            version = version,
            progress = CatalogSyncProgress(stage = stage, overallFraction = fraction),
        )
    }

    private suspend fun applyBundle(bundle: StaticBundleResponse) {
        val resources = bundle.payload.resources
        val catalog = resources.catalog
        val providerIds = buildProviderIdsByTitle(resources.songIds)
        val utageStats = UtageChartStatsIndex(resources.utageStats)
        val favoriteIds = catalogDao.favoriteSongIds().toSet()
        val providerIdToSongIdentifier = mutableMapOf<Int, String>()

        val songs = catalog.songs.mapIndexed { index, remote ->
            SongEntity(
                songIdentifier = remote.songId,
                category = remote.category.orEmpty(),
                title = remote.title?.trim().orEmpty(),
                artist = remote.artist.orEmpty(),
                imageName = remote.imageName?.trim().orEmpty(),
                version = remote.version,
                releaseDate = remote.releaseDate,
                sortOrder = index,
                bpm = remote.bpm,
                isNew = remote.isNew ?: false,
                isLocked = remote.isLocked ?: false,
                comment = remote.comment,
                isFavorite = remote.songId in favoriteIds,
                isRemoved = false,
            )
        }

        val sheets = catalog.songs.flatMap { remote ->
            val ids = providerIds[remote.title.orEmpty()]
                ?: providerIds[normalizeTitle(remote.title.orEmpty())]
                ?: emptyList()
            remote.sheets.map { sheet ->
                val providerSongId = selectProviderId(ids, sheet.type)
                val intlOverride = sheet.regionOverrides?.get("intl")
                val cnOverride = sheet.regionOverrides?.get("cn")
                val utageStat = if (sheet.type.equals("utage", ignoreCase = true)) {
                    utageStats.resolve(
                        providerSongId = providerSongId,
                        songTitle = remote.title.orEmpty(),
                        songIdentifier = remote.songId,
                        sheetDifficulty = sheet.difficulty,
                        sheetLevel = sheet.level,
                    )
                } else {
                    null
                }
                val utageNoteTypes = utageStat?.noteTypes
                if (providerSongId > 0) {
                    providerIdToSongIdentifier[providerSongId] = remote.songId
                }
                SheetEntity(
                    sheetKey = sheetKey(remote.songId, sheet.type, sheet.difficulty),
                    songIdentifier = remote.songId,
                    type = sheet.type,
                    difficulty = sheet.difficulty,
                    version = sheet.version,
                    level = sheet.level,
                    levelValue = sheet.levelValue,
                    internalLevel = sheet.internalLevel,
                    internalLevelValue = sheet.internalLevelValue,
                    noteDesigner = sheet.noteDesigner,
                    intlVersion = intlOverride?.version,
                    intlLevel = intlOverride?.level,
                    intlLevelValue = intlOverride?.levelValue,
                    intlInternalLevel = intlOverride?.internalLevel,
                    intlInternalLevelValue = intlOverride?.internalLevelValue,
                    cnVersion = cnOverride?.version,
                    cnLevel = cnOverride?.level,
                    cnLevelValue = cnOverride?.levelValue,
                    cnInternalLevel = cnOverride?.internalLevel,
                    cnInternalLevelValue = cnOverride?.internalLevelValue,
                    multiverInternalLevelValue = sheet.multiverInternalLevelValue,
                    tap = utageNoteTypes?.tap ?: sheet.noteCounts?.tap,
                    hold = utageNoteTypes?.hold ?: sheet.noteCounts?.hold,
                    slide = utageNoteTypes?.slide ?: sheet.noteCounts?.slide,
                    touch = utageNoteTypes?.touch ?: sheet.noteCounts?.touch,
                    breakCount = utageNoteTypes?.breakCount ?: sheet.noteCounts?.breakCount,
                    total = utageStat?.notes ?: sheet.noteCounts?.total,
                    regionJp = sheet.regions?.get("jp") ?: false,
                    regionIntl = sheet.regions?.get("intl") ?: false,
                    regionUsa = sheet.regions?.get("usa") ?: false,
                    regionCn = sheet.regions?.get("cn") ?: false,
                    providerSongId = providerSongId,
                    isRemoved = false,
                )
            }
        }

        val aliases = resources.aliases?.aliases.orEmpty().flatMap { item ->
            val songIdentifier = providerIdToSongIdentifier[item.songId] ?: return@flatMap emptyList()
            item.aliases
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy { it.lowercase() }
                .map { alias -> SongAliasEntity(songIdentifier, alias) }
        }.distinctBy { "${it.songIdentifier}\u0000${it.alias.lowercase()}" }

        database.withTransaction {
            catalogDao.markAllSongsRemoved()
            catalogDao.markAllSheetsRemoved()
            catalogDao.upsertSongs(songs)
            catalogDao.upsertSheets(sheets)
            catalogDao.upsertCategories(
                catalog.categories.mapIndexed { index, item ->
                    SongCategoryEntity(item.category, index)
                },
            )
            catalogDao.upsertVersions(
                catalog.versions.mapIndexed { index, item ->
                    GameVersionEntity(item.version, item.abbr, item.releaseDate, index)
                },
            )
            catalogDao.deleteAliases()
            catalogDao.upsertAliases(aliases)
        }
        val chartFit = resources.chartFit ?: resources.legacyChartFit
            ?: StaticBundleResponse.ChartFitPayload()
        chartFitStore.save(chartFit)
        mutableChartFit.emit(chartFit)
        danStore.save(resources.danInfo)
        mutableDanCategories.emit(resources.danInfo)
    }

    private fun buildProviderIdsByTitle(items: List<StaticBundleResponse.SongIdItem>): Map<String, List<Int>> {
        val result = mutableMapOf<String, MutableList<Int>>()
        items.forEach { item ->
            result.getOrPut(item.name) { mutableListOf() }.add(item.id)
            result.getOrPut(normalizeTitle(item.name)) { mutableListOf() }.add(item.id)
        }
        return result
    }

    private fun selectProviderId(ids: List<Int>, type: String): Int = when (type.lowercase()) {
        "utage" -> ids.firstOrNull { it >= 100_000 }
        "dx" -> ids.firstOrNull { it in 10_000..<100_000 }
        "std", "standard" -> ids.firstOrNull { it in 1..<10_000 }
        else -> null
    } ?: 0

    private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .lowercase()
        .replace(WhitespaceRegex, " ")

    private inner class StageProgressReporter(
        private val version: String,
        private val stage: CatalogSyncStage,
        private val overallStart: Float,
        private val overallEnd: Float,
        private val useByteProgress: Boolean = false,
    ) {
        private val speedTracker = DownloadSpeedTracker()
        private var completedItems = 0
        private var totalItems = 0
        private var downloadedBytes = 0L
        private var totalBytes = 0L
        private var bytesPerSecond = 0L

        @Synchronized
        fun start(totalItems: Int = 0) {
            this.totalItems = totalItems.coerceAtLeast(0)
            publish()
        }

        @Synchronized
        fun onItemsProgress(completedItems: Int, totalItems: Int) {
            this.completedItems = completedItems.coerceAtLeast(0)
            this.totalItems = totalItems.coerceAtLeast(0)
            publish()
        }

        @Synchronized
        fun onTransfer(byteCount: Long, expectedBytes: Long?) {
            if (expectedBytes != null) totalBytes += expectedBytes.coerceAtLeast(0L)
            val snapshot = speedTracker.addBytes(byteCount)
            downloadedBytes = snapshot.downloadedBytes
            bytesPerSecond = snapshot.bytesPerSecond
            publish()
        }

        @Synchronized
        fun complete() {
            if (totalItems > 0) completedItems = totalItems
            publish(stageFraction = 1f)
        }

        private fun publish(stageFraction: Float = calculatedStageFraction()) {
            val overallFraction = overallStart + (overallEnd - overallStart) * stageFraction
            mutableSyncStatus.value = CatalogSyncStatus.Downloading(
                version = version,
                progress = CatalogSyncProgress(
                    stage = stage,
                    overallFraction = overallFraction.coerceIn(0f, 1f),
                    completedItems = completedItems,
                    totalItems = totalItems,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes.takeIf { it > 0L },
                    bytesPerSecond = bytesPerSecond,
                ),
            )
        }

        private fun calculatedStageFraction(): Float = when {
            useByteProgress && totalBytes > 0L -> downloadedBytes.toFloat() / totalBytes
            totalItems > 0 -> completedItems.toFloat() / totalItems
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    companion object {
        private val WhitespaceRegex = Regex("\\s+")

        fun sheetKey(songIdentifier: String, type: String, difficulty: String): String =
            "${songIdentifier.length}:$songIdentifier|${type.lowercase()}|${difficulty.lowercase()}"
    }
}
