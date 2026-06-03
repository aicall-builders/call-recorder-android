package com.callrecorder.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ===== Auth =====
@Serializable
data class SocialLoginRequest(
    val provider: String = "kakao",
    @SerialName("provider_access_token") val providerAccessToken: String
)

@Serializable
data class AuthResponse(
    @SerialName("firebase_custom_token") val firebaseCustomToken: String? = null,
    @SerialName("custom_token") val customToken: String? = null,     // 기존 호환용
    @SerialName("access_token") val accessToken: String? = null,     // 구버전 서버 호환용
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("firebase_uid") val firebaseUid: String? = null,
    val uid: String? = null,
    val nickname: String? = null,
    val user: User
) {
    val resolvedCustomToken: String
        get() = firebaseCustomToken ?: customToken ?: accessToken ?: ""
}

@Serializable
data class User(
    val id: String,
    val nickname: String,
    val email: String? = null,
    @SerialName("profile_image") val profileImage: String? = null
)

// ===== Store =====
@Serializable
data class CreateStoreRequest(
    val name: String,
    val category: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val address: String? = null
)

@Serializable
data class Store(
    val id: String,
    val name: String,
    val category: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val address: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class StoreList(val stores: List<Store>)

// ===== Calls =====
@Serializable
data class UploadUrlRequest(
    @SerialName("store_id") val storeId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("call_started_at") val callStartedAt: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("counterpart_number") val counterpartNumber: String? = null,
    @SerialName("caller_category") val callerCategory: String? = null
)

@Serializable
data class UploadUrlResponse(
    @SerialName("call_id") val callId: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("upload_headers") val uploadHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class ProcessCallRequest(
    @SerialName("language") val language: String = "ko-KR"
)

/**
 * 통화 1건. 백엔드 실 응답에 맞게 조정됨.
 *
 * 주의:
 *  - status는 백엔드에서 소문자("summarized", "uploaded", "processing", "failed")로 옴.
 *    상수는 [CallStatus]에 정의.
 *  - extracted_info와 keywords는 **JSON 문자열**로 옴(이중 인코딩).
 *    파싱은 [extractedInfoOrNull], [keywordsList] 헬퍼 사용.
 */
@Serializable
data class Call(
    val id: String,
    @SerialName("store_id") val storeId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("caller_number") val callerNumber: String? = null,
    @SerialName("caller_category") val callerCategory: String? = null,
    @SerialName("s3_key") val s3Key: String? = null,
    @SerialName("clova_job_id") val clovaJobId: String? = null,
    @SerialName("stt_result") val sttResult: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    val duration: Int? = null,
    val status: String = "unknown",
    @SerialName("created_at") val createdAt: String? = null,
    val summary: String? = null,
    val category: String? = null,                  // "예약" / "취소" / "불만" / null
    val sentiment: String? = null,                 // "positive" / "negative" / "neutral"
    @SerialName("action_required") val actionRequired: Int? = null,
    @SerialName("is_read") val isRead: Int? = null,
    val keywords: String? = null,                  // JSON 문자열: "[\"a\", \"b\"]"
    @SerialName("extracted_info") val extractedInfoRaw: String? = null,  // JSON 문자열
)

/** 통화 상태 상수 (백엔드 소문자 응답에 맞춤) */
object CallStatus {
    const val UPLOADED = "uploaded"
    const val PROCESSING = "processing"
    const val SUMMARIZED = "summarized"
    const val FAILED = "failed"
    const val UNKNOWN = "unknown"
}

/** 카테고리 코드 (extracted_info.category_code) */
object CallCategoryCode {
    const val RESERVATION = "reservation"   // 예약
    const val CANCEL = "cancel"              // 취소
    const val COMPLAINT = "complaint"        // 불만
    const val INQUIRY = "inquiry"            // 문의
    const val OTHER = "other"
}

/** 한글 카테고리 라벨 (Call.category 필드값) */
object CallCategoryLabel {
    const val RESERVATION = "예약"
    const val CANCEL = "취소"
    const val COMPLAINT = "불만"
    const val INQUIRY = "문의"
}

/**
 * LLM이 추출한 구조화 정보.
 * 백엔드는 DB JSON 컬럼을 stringified 로 내려줌 -> [Call.extractedInfoOrNull]에서 파싱.
 */
@Serializable
data class ExtractedInfo(
    @SerialName("customer_name") val customerName: String? = null,
    val date: String? = null,            // "2023-10-25"
    val time: String? = null,            // "19:00"
    @SerialName("party_size") val partySize: Int? = null,
    val phone: String? = null,           // "010-1234-5678"
    val menu: List<String> = emptyList(),
    @SerialName("special_notes") val specialNotes: String? = null,
    @SerialName("category_code") val categoryCode: String? = null,
)

/** 안전하게 키워드 배열로 변환 (실패 시 빈 리스트) */
fun Call.keywordsList(): List<String> {
    val raw = keywords ?: return emptyList()
    return runCatching {
        DtoJson.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())
}

/** extracted_info JSON 문자열을 파싱. 실패 시 null. */
fun Call.extractedInfoOrNull(): ExtractedInfo? {
    val raw = extractedInfoRaw ?: return null
    return runCatching {
        DtoJson.decodeFromString<ExtractedInfo>(raw)
    }.getOrNull()
}

/** Dto 내부 JSON 파서 (느슨한 설정) */
internal val DtoJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class CallList(val calls: List<Call>)

@Serializable
data class CallDetail(
    val call: Call,
    val transcript: String? = null,
    val summary: Summary? = null
)

/**
 * 음성 파일 presigned URL 응답.
 * 백엔드가 어떤 키로 내려줄지 모르므로 두 가지 흔한 이름 다 받음.
 */
@Serializable
data class AudioUrlResponse(
    val url: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
) {
    val resolved: String? get() = url ?: audioUrl
}

/**
 * 구버전 Summary 스키마 - 일부 엔드포인트(/summaries/{id})에서 여전히 사용될 수 있어 유지.
 * 새 화면에서는 Call.summary(평문) + Call.extractedInfoOrNull() 조합을 우선 사용 권장.
 */
@Serializable
data class Summary(
    val id: String,
    @SerialName("call_id") val callId: String,
    val title: String = "",
    val gist: String = "",
    @SerialName("action_items") val actionItems: List<String> = emptyList(),
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    val sentiment: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)