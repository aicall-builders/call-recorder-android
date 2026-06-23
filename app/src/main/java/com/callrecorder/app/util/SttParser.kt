package com.callrecorder.app.util

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
    BOT,        // [화자2] - 우측 파란 말풍선 (AI 통화 비서/업주)
    UNKNOWN,    // 라벨 없는 경우
}

object SttParser {
    private val LABEL_REGEX = Regex("""\[화자(\d+)]\s*:\s*""")

    fun parse(stt: String?): List<SttMessage> {
        if (stt.isNullOrBlank()) return emptyList()

        // [화자N]: 라벨이 하나라도 있으면 라벨 기준 분할
        val matches = LABEL_REGEX.findAll(stt).toList()
        if (matches.isEmpty()) {
            return listOf(SttMessage(SttSpeaker.UNKNOWN, stt.trim()))
        }

        val result = mutableListOf<SttMessage>()
        for (i in matches.indices) {
            val m = matches[i]
            val speakerNum = m.groupValues[1].toIntOrNull() ?: 0
            val start = m.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else stt.length
            val text = stt.substring(start, end).trim()
            if (text.isBlank()) continue

            val speaker = when (speakerNum) {
                1 -> SttSpeaker.BOT       // 화자1 = 수신자(사장님)
                2 -> SttSpeaker.CUSTOMER
                else -> SttSpeaker.UNKNOWN
            }
            result += SttMessage(speaker, text)
        }
        return result
    }
}