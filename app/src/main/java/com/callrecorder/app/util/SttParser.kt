package com.callrecorder.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 통화 원문(STT) 파서.
 *
 * 입력 예:
 *   "[화자1]: 여보세요. 명동 칼국수죠 네 맞습니다.
 *    [화자1]: 김민수입니다. 010 1 2 3 4 5 6 7 8이에요."
 *
 * 출력: SttMessage 리스트.
 *
 * 주의: 백엔드가 [화자1]만 보낼 수도, [화자1]/[화자2] 둘 다 섞을 수도 있다.
 *       [화자1] = 고객(왼쪽 회색), [화자2] = 비서/업주(오른쪽 파랑) 으로 매핑.
 *       라벨이 전혀 없으면 전체를 한 메시지로.
 */
data class SttMessage(
    val speaker: SttSpeaker,
    val text: String,
)

enum class SttSpeaker {
    CUSTOMER,   // [화자1] - 좌측 회색 말풍선
    BOT,        // [화자2] - 우측 파란 말풍선 (FIANO/업주)
    UNKNOWN,    // 라벨 없는 경우
}

object SttParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val LABEL_REGEX = Regex(
        pattern = """(?:\[?\s*((?:화자|speaker|spk|참여자)\s*[_-]?\s*\d+|발신자|수신자|고객|상담원|직원|비서|agent|customer|caller|receiver|sender)\s*]?\s*[:：])""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parse(stt: String?): List<SttMessage> {
        if (stt.isNullOrBlank()) return emptyList()
        val normalized = stt.unescapeTranscriptText().trim()

        parseJsonTranscript(normalized)?.let { parsed ->
            if (parsed.isNotEmpty()) return parsed
        }

        return parseLabeledOrPlainTranscript(normalized)
    }

    private fun parseLabeledOrPlainTranscript(raw: String): List<SttMessage> {
        val normalized = raw.unescapeTranscriptText().trim()
        if (normalized.isBlank()) return emptyList()

        val matches = LABEL_REGEX.findAll(normalized).toList()
        if (matches.isEmpty()) return splitPlainTranscript(normalized)

        val result = mutableListOf<SttMessage>()
        for (i in matches.indices) {
            val m = matches[i]
            val label = m.groupValues[1]
            val start = m.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else normalized.length
            val text = normalized.substring(start, end).trim()
            if (text.isBlank()) continue

            result += SttMessage(label.toSpeaker(), text)
        }
        return result.coalesced()
    }

    private fun parseJsonTranscript(raw: String): List<SttMessage>? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val messages = extractMessages(root)
        return messages.coalesced()
    }

    private fun extractMessages(element: JsonElement): List<SttMessage> {
        return when (element) {
            is JsonArray -> element.flatMap { extractMessages(it) }
            is JsonObject -> {
                val segmentArray = firstArray(
                    element,
                    "segments",
                    "utterances",
                    "messages",
                    "results",
                    "items",
                    "transcripts",
                    "transcript_segments",
                )
                if (segmentArray != null) {
                    segmentArray.flatMap { extractMessages(it) }
                } else {
                val nested = firstObject(element, "result", "data", "response")
                    if (nested != null) {
                        extractMessages(nested)
                    } else {
                        val text = element.firstString(
                            "text",
                            "utterance",
                            "sentence",
                            "content",
                            "message",
                            "transcript",
                        )
                        if (text.isNullOrBlank()) {
                            emptyList()
                        } else {
                            val speaker = element.speakerValue().toSpeaker()
                            val normalizedText = text.unescapeTranscriptText().trim()
                            if (LABEL_REGEX.containsMatchIn(normalizedText) || speaker == SttSpeaker.UNKNOWN) {
                                parseLabeledOrPlainTranscript(normalizedText)
                            } else {
                                listOf(SttMessage(speaker, normalizedText))
                            }
                        }
                    }
                }
            }
            is JsonPrimitive -> splitPlainTranscript(element.contentOrNull.orEmpty())
        }
    }

    private fun firstArray(obj: JsonObject, vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { key -> obj[key] as? JsonArray }

    private fun firstObject(obj: JsonObject, vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { key -> obj[key] as? JsonObject }

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.asStringOrNull() }

    private fun JsonObject.speakerValue(): String? {
        firstString(
            "speaker",
            "speakerLabel",
            "speaker_label",
            "speakerName",
            "speaker_name",
            "speakerId",
            "speaker_id",
            "speakerTag",
            "speaker_tag",
            "role",
            "channel",
            "label",
        )?.let { return it }
        val speakerObj = this["speaker"] as? JsonObject
        return speakerObj?.firstString("label", "name", "id", "role")
    }

    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.jsonPrimitive?.contentOrNull

    private fun String?.toSpeaker(): SttSpeaker {
        val value = this?.trim()?.lowercase().orEmpty().replace(" ", "")
        val speakerNumber = Regex("""(?:화자|speaker|spk|참여자)[_-]?0*(\d+)""")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return when {
            value.isBlank() -> SttSpeaker.UNKNOWN
            speakerNumber == 0 -> SttSpeaker.CUSTOMER
            speakerNumber == 1 -> SttSpeaker.BOT
            speakerNumber == 2 -> SttSpeaker.CUSTOMER
            value == "0" || value.contains("speaker0") || value.contains("spk0") ||
                value.contains("speaker_00") || value.contains("speaker00") -> SttSpeaker.CUSTOMER
            value == "1" || value.contains("화자1") || value.contains("speaker1") -> SttSpeaker.BOT
            value == "2" || value.contains("화자2") || value.contains("speaker2") -> SttSpeaker.CUSTOMER
            value.contains("수신") || value.contains("상담") || value.contains("직원") ||
                value.contains("비서") || value.contains("agent") || value.contains("receiver") -> SttSpeaker.BOT
            value.contains("발신") || value.contains("고객") || value.contains("손님") ||
                value.contains("customer") || value.contains("caller") || value.contains("sender") -> SttSpeaker.CUSTOMER
            else -> SttSpeaker.UNKNOWN
        }
    }

    private fun splitPlainTranscript(raw: String): List<SttMessage> {
        val cleaned = raw
            .replace(Regex("""^\s*["']|["']\s*$"""), "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val lines = cleaned
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val chunks = if (lines.size > 1) lines else cleaned.chunkBySentences()
        return chunks.map { SttMessage(SttSpeaker.UNKNOWN, it) }
    }

    private fun String.chunkBySentences(maxChars: Int = 140): List<String> {
        val sentences = Regex("""[^.!?。！？\n]+[.!?。！？]?""")
            .findAll(this)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (sentences.isEmpty()) return listOf(this.trim())

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        sentences.forEach { sentence ->
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxChars) {
                chunks += current.toString().trim()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks
    }

    private fun String.unescapeTranscriptText(): String =
        replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")

    private fun List<SttMessage>.coalesced(): List<SttMessage> {
        val result = mutableListOf<SttMessage>()
        forEach { message ->
            val text = message.text.trim()
            if (text.isBlank()) return@forEach
            val last = result.lastOrNull()
            if (last != null && last.speaker == message.speaker && message.speaker != SttSpeaker.UNKNOWN) {
                result[result.lastIndex] = last.copy(text = "${last.text}\n$text")
            } else {
                result += message.copy(text = text)
            }
        }
        return result
    }
}
