package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class OtogamePlaylogResponse(
    val code: String = "",
    val message: String = "",
    val data: OtogamePlaylogPage = OtogamePlaylogPage(),
)

@Serializable
internal data class OtogamePlaylogPage(
    val data: List<OtogamePlaylog> = emptyList(),
    val pagination: OtogamePagination = OtogamePagination(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class OtogamePagination(
    val page: Int = 1,
    @SerialName("per_page")
    @JsonNames("perPage")
    val perPage: Int = 0,
    @SerialName("total_page")
    @JsonNames("totalPage")
    val totalPage: Int = 1,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class OtogamePlaylog(
    val music: OtogameMusic = OtogameMusic(),
    val difficulty: Int = -1,
    @SerialName("level_info")
    @JsonNames("levelInfo")
    val levelInfo: OtogameLevelInfo = OtogameLevelInfo(),
    @SerialName("track_no")
    @JsonNames("trackNo")
    val trackNo: Int = 0,
    @SerialName("play_date")
    @JsonNames("playDate")
    val playDate: Long = 0,
    val achievement: Long = 0,
    @SerialName("score_rank")
    @JsonNames("scoreRank")
    val scoreRank: Int = 0,
    @SerialName("deluxe_score")
    @JsonNames("deluxeScore")
    val deluxeScore: Int = 0,
    @SerialName("combo_status")
    @JsonNames("comboStatus")
    val comboStatus: Int = 0,
    @SerialName("sync_status")
    @JsonNames("syncStatus")
    val syncStatus: Int = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class OtogameMusic(
    @SerialName("music_id")
    @JsonNames("musicId")
    @Serializable(with = OtogameMusicIdSerializer::class)
    val musicId: String = "",
    val name: String = "",
    @SerialName("is_deluxe")
    @JsonNames("isDeluxe")
    val isDeluxe: Boolean = false,
    @SerialName("utage_kanji_name")
    @JsonNames("utageKanjiName")
    val utageKanjiName: String? = null,
)

@Serializable
internal data class OtogameLevelInfo(
    val difficulty: Int = -1,
    val level: Int = 0,
)

private object OtogameMusicIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OtogameMusicId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
