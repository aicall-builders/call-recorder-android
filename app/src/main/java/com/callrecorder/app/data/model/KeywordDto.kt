package com.callrecorder.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 서버가 불리언(true/false) 또는 숫자(0/1) 중 무엇으로 주든 Int(0/1)로 안전하게 파싱.
 * (list 엔드포인트는 0/1, create 엔드포인트는 true/false 로 응답하는 불일치 대응)
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val prim = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        prim.booleanOrNull?.let { return if (it) 1 else 0 }
        return prim.content.toIntOrNull()
            ?: prim.content.toDoubleOrNull()?.toInt()
            ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

@Serializable
data class CustomKeyword(
    val id: String = "",
    val keyword: String = "",
    val label: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("action_required") val actionRequired: Int = 1,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("is_enabled") val isEnabled: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CustomKeywordList(
    val keywords: List<CustomKeyword> = emptyList(),
)

@Serializable
data class CreateKeywordRequest(
    val keyword: String,
    val label: String? = null,
    @SerialName("action_required") val actionRequired: Boolean = true,
)

@Serializable
data class UpdateKeywordRequest(
    @SerialName("is_enabled") val isEnabled: Boolean,
)