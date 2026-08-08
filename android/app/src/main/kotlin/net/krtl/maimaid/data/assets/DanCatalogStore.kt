package net.krtl.maimaid.data.assets

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

@Serializable
data class DanCategory(
    val title: String,
    val id: String,
    val description: String? = null,
    val isHidden: Boolean = false,
    val sections: List<DanSection> = emptyList()
)

@Serializable
data class DanSection(
    val title: String? = null,
    val description: String? = null,
    val sheets: List<String> = emptyList(),
    val sheetDescriptions: List<String>? = null
)

data class DanSheetRef(
    val title: String,
    val type: String,
    val difficulty: String,
    val extra: String? = null
) {
    val isPlaceholder: Boolean
        get() = type.isBlank() || difficulty.isBlank()

    companion object {
        fun from(raw: String): DanSheetRef {
            val parts = raw.split("|")
            return DanSheetRef(
                title = parts.getOrNull(0).orEmpty(),
                type = parts.getOrNull(1).orEmpty(),
                difficulty = parts.getOrNull(2).orEmpty(),
                extra = parts.getOrNull(3)
            )
        }
    }
}

object DanCatalogStore {
    private const val CACHE_FILE_NAME = "dan_data.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val yaml = Yaml(
        LoaderOptions().apply {
            maxAliasesForCollections = 512
        }
    )
    private val cacheMutex = Mutex()

    @Volatile
    private var cachedCategories: List<DanCategory>? = null

    suspend fun load(context: Context): List<DanCategory> {
        cachedCategories?.let { return it }
        return cacheMutex.withLock {
            cachedCategories ?: withContext(Dispatchers.IO) {
                val file = cacheFile(context)
                if (!file.exists()) {
                    emptyList()
                } else {
                    runCatching {
                        json.decodeFromString<List<DanCategory>>(file.readText())
                    }.getOrDefault(emptyList())
                        .let(::sanitizeDanCategories)
                        .also { cachedCategories = it }
                }
            }
        }
    }

    suspend fun replaceFromYaml(context: Context, yamlString: String): List<DanCategory> = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            val parsed = parseYaml(yamlString)
            val sanitized = sanitizeDanCategories(parsed)
            val file = cacheFile(context)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(sanitized))
            cachedCategories = sanitized
            sanitized
        }
    }

    suspend fun replaceFromJson(context: Context, jsonString: String): List<DanCategory> = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            val parsed = json.decodeFromString<List<DanCategory>>(jsonString)
            val sanitized = sanitizeDanCategories(parsed)
            val file = cacheFile(context)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(sanitized))
            cachedCategories = sanitized
            sanitized
        }
    }

    suspend fun clearCache(context: Context) = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            cacheFile(context).delete()
            cachedCategories = null
        }
    }

    private fun parseYaml(yamlString: String): List<DanCategory> {
        val loaded = yaml.load<Any?>(yamlString)
        val jsonElement = loaded.toJsonElement()
        return json.decodeFromJsonElement(jsonElement)
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun sanitizeDanCategories(categories: List<DanCategory>): List<DanCategory> {
        return buildList {
            categories.forEach { category ->
                val categoryTitle = category.title.trim()
                val lowerTitle = categoryTitle.lowercase()
                if (lowerTitle.contains("test") || lowerTitle.contains("author's choice")) {
                    return@forEach
                }

                val cleanedSections = buildList {
                    category.sections.forEach { section ->
                        val cleanedRefs = section.sheets.filter(::isValidDanRawSheetRef)
                        if (cleanedRefs.isEmpty()) return@forEach

                        val cleanedDescriptions = section.sheetDescriptions?.let { descriptions ->
                            section.sheets.zip(descriptions)
                                .filter { (sheet, _) -> isValidDanRawSheetRef(sheet) }
                                .map { (_, description) -> description }
                                .ifEmpty { null }
                        }

                        add(
                            DanSection(
                                title = section.title?.trim()?.takeIf { it.isNotEmpty() },
                                description = section.description?.trim()?.takeIf { it.isNotEmpty() },
                                sheets = cleanedRefs,
                                sheetDescriptions = cleanedDescriptions
                            )
                        )
                    }
                }

                if (cleanedSections.isEmpty()) return@forEach

                add(
                    DanCategory(
                        title = category.title,
                        id = category.id,
                        description = category.description,
                        isHidden = category.isHidden,
                        sections = cleanedSections
                    )
                )
            }
        }.filterNot { it.isHidden }
    }

    private fun isValidDanRawSheetRef(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false

        val ref = DanSheetRef.from(trimmed)
        if (ref.title.trim().isEmpty() || ref.isPlaceholder) return false

        val type = ref.type.lowercase()
        val difficulty = ref.difficulty.lowercase()

        if (type.contains("utage") || difficulty.contains("utage")) return false
        if (type !in setOf("dx", "std")) return false
        if (difficulty !in setOf("basic", "advanced", "expert", "master", "remaster")) return false

        return true
    }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
