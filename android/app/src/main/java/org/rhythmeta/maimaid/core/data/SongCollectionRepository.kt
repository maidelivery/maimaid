package org.rhythmeta.maimaid.core.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.rhythmeta.maimaid.core.database.SongCollectionDao
import org.rhythmeta.maimaid.core.database.SongCollectionEntity
import org.rhythmeta.maimaid.core.database.SongCollectionItemEntity

class SongCollectionRepository(private val dao: SongCollectionDao) {
    val collections: Flow<List<SongCollectionEntity>> = dao.observeCollections()
    val items: Flow<List<SongCollectionItemEntity>> = dao.observeItems()

    suspend fun create(name: String): SongCollectionEntity {
        val baseName = name.trim()
        require(baseName.isNotEmpty())
        val now = System.currentTimeMillis()
        val existingCollections = dao.collectionsIncludingDeleted()
        val nextPosition = existingCollections.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0
        val activeNames = existingCollections
            .asSequence()
            .filter { it.deletedAt == null }
            .mapTo(mutableSetOf(), SongCollectionEntity::name)
        val value = SongCollectionEntity(
            UUID.randomUUID().toString(),
            uniqueName(baseName, activeNames),
            nextPosition,
            now,
            now,
            clientUpdatedAt = now,
        )
        dao.upsertCollection(value)
        return value
    }

    suspend fun rename(collection: SongCollectionEntity, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val now = System.currentTimeMillis()
        dao.upsertCollection(collection.copy(name = trimmed.take(40), updatedAt = now, clientUpdatedAt = now))
    }

    suspend fun delete(collection: SongCollectionEntity) {
        val now = System.currentTimeMillis()
        dao.upsertCollection(collection.copy(deletedAt = now, updatedAt = now, clientUpdatedAt = now))
        dao.itemsIncludingDeleted()
            .filter { it.collectionId == collection.id && it.deletedAt == null }
            .forEach { dao.upsertItem(it.copy(deletedAt = now, updatedAt = now, clientUpdatedAt = now)) }
    }

    suspend fun deleteItem(item: SongCollectionItemEntity) {
        val now = System.currentTimeMillis()
        dao.upsertItem(item.copy(deletedAt = now, updatedAt = now, clientUpdatedAt = now))
    }

    suspend fun reorderItems(collectionId: String, orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        val byId = dao.itemsIncludingDeleted().associateBy(SongCollectionItemEntity::id)
        orderedIds.forEachIndexed { position, id ->
            byId[id]?.let { dao.upsertItem(it.copy(position = position, updatedAt = now, clientUpdatedAt = now)) }
        }
    }

    suspend fun importCollection(source: SongCollectionExport): SongCollectionEntity {
        val existingCollections = dao.collectionsIncludingDeleted()
        val activeNames = existingCollections
            .asSequence()
            .filter { it.deletedAt == null }
            .mapTo(mutableSetOf(), SongCollectionEntity::name)
        val name = uniqueName(
            source.name.trim().ifEmpty { "Collection" },
            activeNames,
        )
        val now = System.currentTimeMillis()
        val collection = SongCollectionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            sortIndex = existingCollections.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0,
            createdAt = now,
            updatedAt = now,
            clientUpdatedAt = now,
        )
        dao.upsertCollection(collection)
        source.entries
            .distinctBy { Triple(it.songId, it.chartType.lowercase(), it.difficulty.lowercase()) }
            .forEachIndexed { index, entry ->
                dao.upsertItem(
                    SongCollectionItemEntity(
                        id = UUID.randomUUID().toString(),
                        collectionId = collection.id,
                        songId = entry.songId,
                        chartType = entry.chartType.lowercase(),
                        difficulty = entry.difficulty.lowercase(),
                        position = index,
                        createdAt = now,
                        updatedAt = now,
                        clientUpdatedAt = now,
                    ),
                )
            }
        return collection
    }

    private fun uniqueName(rawName: String, existingNames: Set<String>): String {
        val baseName = rawName.trim().ifEmpty { "Collection" }.take(40)
        if (baseName !in existingNames) return baseName
        var suffix = 2
        while (true) {
            val suffixText = " ($suffix)"
            val prefixLength = (40 - suffixText.length).coerceAtLeast(1)
            val candidate = baseName.take(prefixLength) + suffixText
            if (candidate !in existingNames) return candidate
            suffix++
        }
    }

    suspend fun setMembership(collection: SongCollectionEntity, songId: String, chartType: String, difficulty: String, included: Boolean) {
        val existing = dao.itemsIncludingDeleted().firstOrNull { it.collectionId == collection.id && it.songId == songId && it.chartType.equals(chartType, true) && it.difficulty.equals(difficulty, true) }
        val now = System.currentTimeMillis()
        if (included) {
            dao.upsertItem(existing?.copy(deletedAt = null, updatedAt = now, clientUpdatedAt = now) ?: SongCollectionItemEntity(UUID.randomUUID().toString(), collection.id, songId, chartType.lowercase(), difficulty.lowercase(), dao.itemsIncludingDeleted().count { it.collectionId == collection.id }, now, now, null, now))
        } else if (existing != null) dao.upsertItem(existing.copy(deletedAt = now, updatedAt = now, clientUpdatedAt = now))
    }
}
