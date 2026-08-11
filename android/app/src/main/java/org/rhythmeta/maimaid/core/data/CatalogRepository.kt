package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    private val catalogDao: CatalogDao = database.catalogDao()
    private val syncMutex = Mutex()
    private val mutableSyncStatus = MutableStateFlow<CatalogSyncStatus>(CatalogSyncStatus.Checking)

    val syncStatus = mutableSyncStatus.asStateFlow()
    val songCount: Flow<Int> = catalogDao.observeSongCount()
    val sheetCount: Flow<Int> = catalogDao.observeSheetCount()
    val featuredSongs: Flow<List<SongEntity>> = catalogDao.observeFeaturedSongs(limit = 6)
    val songs: Flow<List<SongEntity>> = catalogDao.observeSongs()
    val sheets: Flow<List<SheetEntity>> = catalogDao.observeSheets()
    val aliases: Flow<List<SongAliasEntity>> = catalogDao.observeAliases()
    val versions: Flow<List<GameVersionEntity>> = catalogDao.observeVersions()
    val categories: Flow<List<SongCategoryEntity>> = catalogDao.observeCategories()

    fun observeSheetsForSong(songIdentifier: String): Flow<List<SheetEntity>> =
        catalogDao.observeSheetsForSong(songIdentifier)

    fun observeAliasesForSong(songIdentifier: String): Flow<List<String>> =
        catalogDao.observeAliasesForSong(songIdentifier)

    suspend fun fetchStaticManifest(): StaticManifest = client.fetchManifest()

    suspend fun currentStaticDataMd5(): String? = syncStateStore.currentMd5()

    suspend fun setFavorite(songIdentifier: String, isFavorite: Boolean) {
        catalogDao.setFavorite(songIdentifier, isFavorite)
    }

    suspend fun refresh(force: Boolean = false) = syncMutex.withLock {
        val localSongCount = catalogDao.songCount()
        mutableSyncStatus.value = CatalogSyncStatus.Checking
        runCatching {
            val manifest = client.fetchManifest()
            val currentMd5 = syncStateStore.currentMd5()
            if (localSongCount > 0 && currentMd5 == manifest.md5 && !force) {
                downloadMissingCovers(catalogDao.imageNames())
                mutableSyncStatus.value = CatalogSyncStatus.Ready(manifest.version, fromCache = true)
                return@runCatching
            }

            mutableSyncStatus.value = CatalogSyncStatus.Downloading(manifest.version)
            val bundle = client.fetchBundle(manifest.version)
            applyBundle(bundle)
            downloadMissingCovers(bundle.payload.resources.catalog.songs.map { it.imageName.orEmpty() })
            syncStateStore.save(bundle.version, bundle.md5)
            mutableSyncStatus.value = CatalogSyncStatus.Ready(bundle.version, fromCache = false)
        }.onFailure { error ->
            mutableSyncStatus.value = CatalogSyncStatus.Failed(
                message = error.message ?: error::class.java.simpleName,
                hasLocalCatalog = localSongCount > 0,
            )
        }
    }

    private suspend fun downloadMissingCovers(imageNames: Iterable<String>) {
        coverImageStore.downloadMissing(imageNames) { imageName, destination ->
            client.downloadCover(imageName, destination)
        }
    }

    private suspend fun applyBundle(bundle: StaticBundleResponse) {
        val resources = bundle.payload.resources
        val catalog = resources.catalog
        val providerIds = buildProviderIdsByTitle(resources.songIds)
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
                    tap = sheet.noteCounts?.tap,
                    hold = sheet.noteCounts?.hold,
                    slide = sheet.noteCounts?.slide,
                    touch = sheet.noteCounts?.touch,
                    breakCount = sheet.noteCounts?.breakCount,
                    total = sheet.noteCounts?.total,
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

    companion object {
        private val WhitespaceRegex = Regex("\\s+")

        fun sheetKey(songIdentifier: String, type: String, difficulty: String): String =
            "${songIdentifier.length}:$songIdentifier|${type.lowercase()}|${difficulty.lowercase()}"
    }
}
