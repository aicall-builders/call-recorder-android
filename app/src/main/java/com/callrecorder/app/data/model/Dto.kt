package com.callrecorder.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

// ===== Auth =====
@Serializable
data class KakaoLoginRequest(
    @SerialName("provider_access_token")
    val providerAccessToken: String
)
@Serializable
data class AuthResponse(
    @SerialName("custom_token") val customToken: String,
    @SerialName("access_token") val accessToken: String? = null,   // 호환용 (서버가 같은 값을 또 줌)
    @SerialName("refresh_token") val refreshToken: String? = null,
    val uid: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val user: User? = null
)

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
    @SerialName("caller_name") val callerName: String? = null,
    @SerialName("caller_category") val callerCategory: String? = null,
    @SerialName("direction") val direction: String? = null,
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
    val keywords: JsonElement? = null,                  // 문자열 또는 배열 둘 다 허용
    @SerialName("extracted_info") val extractedInfoRaw: JsonElement? = null,  // 문자열 또는 객체
    @SerialName("internal_keywords") val internalKeywordsRaw: JsonElement? = null, // 문자열 또는 객체
)

/** 발신자 정보 수정 요청 (PATCH /calls/{id}) */
@Serializable
data class UpdateCallRequest(
    @SerialName("caller_number") val callerNumber: String,
    @SerialName("caller_name") val callerName: String,
)

/** 통화 상태 상수 (백엔드 소문자 응답에 맞춤)
 *
 * 실제 백엔드(call_handler.py) 흐름:
 *   uploaded → transcribed → completed   (실패는 error)
 * SUMMARIZED("summarized")는 구버전 호환용.
 */
object CallStatus {
    const val UPLOADED = "uploaded"
    const val PROCESSING = "processing"
    const val TRANSCRIBED = "transcribed"
    const val COMPLETED = "completed"
    const val SUMMARIZED = "summarized"
    const val FAILED = "failed"
    const val ERROR = "error"
    const val UNKNOWN = "unknown"
}

/** 분석(요약)이 끝난 통화인지 판정.
 *  status가 completed/summarized 거나, 요약 텍스트가 있으면 완료로 본다(안전망). */
fun Call.isAnalyzed(): Boolean {
    val s = status.lowercase()
    return s == CallStatus.COMPLETED ||
            s == CallStatus.SUMMARIZED ||
            !summary.isNullOrBlank()
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

/** JsonElement를 문자열로 안전하게 변환 */
fun JsonElement?.toStringOrNull(): String? {
    if (this == null) return null
    return when (this) {
        is JsonPrimitive -> content
        else -> toString()
    }
}

/** JsonElement를 Map<String,String>으로 안전하게 변환 */
fun JsonElement?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return runCatching {
        when (this) {
            // 이미 객체면 바로 변환
            is JsonObject -> this.entries
                .filter { !it.key.startsWith("_") }  // _로 시작하는 내부 키 제거
                .mapNotNull { (k, v) ->
                    val strVal = when (v) {
                        is JsonPrimitive -> v.content
                        else -> null  // 객체/배열 값은 제외
                    }
                    if (strVal != null) k to strVal else null
                }.toMap()
            // 문자열이면 JSON 파싱
            is JsonPrimitive -> {
                val parsed = DtoJson.parseToJsonElement(content)
                if (parsed is JsonObject) {
                    parsed.entries
                        .filter { !it.key.startsWith("_") }
                        .mapNotNull { (k, v) ->
                            val strVal = (v as? JsonPrimitive)?.content
                            if (strVal != null) k to strVal else null
                        }.toMap()
                } else emptyMap()
            }
            else -> emptyMap()
        }
    }.getOrDefault(emptyMap())
}

/** 안전하게 키워드 배열로 변환 (실패 시 빈 리스트) */
fun Call.keywordsList(): List<String> {
    val element = keywords ?: return emptyList()
    return runCatching {
        when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> DtoJson.decodeFromString<List<String>>(element.content)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

/** extracted_info를 파싱. 실패 시 null. */
fun Call.extractedInfoOrNull(): ExtractedInfo? {
    val element = extractedInfoRaw ?: return null
    return runCatching {
        when (element) {
            is JsonPrimitive -> DtoJson.decodeFromString<ExtractedInfo>(element.content)
            is JsonObject -> DtoJson.decodeFromJsonElement(ExtractedInfo.serializer(), element)
            else -> null
        }
    }.getOrNull()
}

/** internal_keywords를 Map<String,String>으로 변환 */
fun Call.internalKeywordsMap(): Map<String, String> = internalKeywordsRaw.toStringMap()

/** internal_keywords를 JSON 문자열로 변환 (기존 코드 호환용) */
fun Call.internalKeywordsString(): String? = internalKeywordsRaw.toStringOrNull()

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

// ===== Calendar =====
@Serializable
data class CalendarConnection(
    val id: String,
    val provider: String,
    @SerialName("calendar_name") val calendarName: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CalendarConnectionList(
    val connections: List<CalendarConnection>
)

@Serializable
data class CalendarOAuthUrlResponse(
    @SerialName("authorize_url") val authUrl: String
)

@Serializable
data class CalendarEventResponse(
    val success: Boolean = false,
    val provider: String? = null,
    val title: String? = null,
    @SerialName("event_url") val eventUrl: String? = null,
    @SerialName("demo_fallback") val demoFallback: Boolean = false,
    val message: String? = null,
)

// 앱이 직접 OAuth 코드를 완료할 때 사용 (POST /calendar/connections/oauth-code)
@Serializable
data class CalendarOAuthCodeRequest(
    val provider: String,
    @SerialName("authorization_code") val authorizationCode: String,
    @SerialName("redirect_uri") val redirectUri: String,
    val state: String = "",
)

@Serializable
data class CalendarConnectionWrapper(
    val connection: CalendarConnection? = null,
)


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
// ===== Naver Login =====
@Serializable
data class NaverLoginRequest(
    @SerialName("provider_access_token")
    val providerAccessToken: String,
)
// ===== 구글 로그인 요청 =====
@Serializable
data class GoogleLoginRequest(
    @SerialName("provider_access_token")
    val providerAccessToken: String,
)
// ===== Customer (고객 프로필 + AI 분석) =====

/** 고객 편집 필드 (GET /customers/{phone} 의 profile) */
@Serializable
data class CustomerProfile(
    val email: String? = null,
    val tendency: String? = null,          // 고객성향
    val medical: String? = null,           // 병력
    @SerialName("special_notes") val specialNotes: String? = null,  // 특이사항
    @SerialName("custom_fields") val customFields: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
)

/** 고객 AI 분석 (GET /customers/{phone} 의 analysis) */
@Serializable
data class CustomerAnalysis(
    val analysis: String? = null,
    @SerialName("call_count") val callCount: Int? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

/** GET /customers/{phone} 응답 */
@Serializable
data class CustomerProfileResponse(
    val profile: CustomerProfile? = null,
    val analysis: CustomerAnalysis? = null,
)

/** PATCH /customers/{phone} 요청 */
@Serializable
data class UpdateCustomerRequest(
    val email: String? = null,
    val tendency: String? = null,
    val medical: String? = null,
    @SerialName("special_notes") val specialNotes: String? = null,
    @SerialName("custom_fields") val customFields: Map<String, String>? = null,
    @SerialName("is_pinned") val isPinned: Boolean? = null,
)


// ===== Customer extended APIs =====

@Serializable
data class CustomerListResponse(
    val customers: List<CustomerListItem> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class CustomerListItem(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val phone: String,
    val name: String? = null,
    val email: String? = null,
    val tendency: String? = null,
    val medical: String? = null,
    @SerialName("special_notes") val specialNotes: String? = null,
    @SerialName("custom_fields") val customFields: JsonElement? = null,
    @SerialName("consent_status") val consentStatus: String = "pending",
    @SerialName("consented_at") val consentedAt: String? = null,
    @SerialName("consent_revoked_at") val consentRevokedAt: String? = null,
    @SerialName("consent_version") val consentVersion: String? = null,
    @SerialName("call_count") val callCount: Int = 0,
    @SerialName("last_call_at") val lastCallAt: String? = null,
    @SerialName("latest_summary") val latestSummary: String? = null,
    @SerialName("latest_category") val latestCategory: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
)

@Serializable
data class CreateConsentLinkRequest(
    val name: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("store_id") val storeId: String? = null,
)

@Serializable
data class ConsentLinkResponse(
    val phone: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("consent_status") val consentStatus: String? = null,
    val token: String? = null,
    @SerialName("consent_url") val consentUrl: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class CustomerHistoryResponse(
    val phone: String,
    val items: List<CustomerHistoryItem> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class CustomerHistoryItem(
    val type: String,
    val id: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val category: String? = null,
    val memo: String? = null,
    val status: String? = null,
    @SerialName("is_anonymized") val isAnonymized: Boolean = false,
    val photos: List<CustomerHistoryPhoto> = emptyList(),
)

@Serializable
data class CustomerHistoryPhoto(
    val id: String? = null,
    val url: String? = null,
    val caption: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateCustomerMemoRequest(
    val memo: String,
    @SerialName("is_anonymized") val isAnonymized: Boolean = false,
)

@Serializable
data class CustomerMemoResponse(
    val id: String,
    val phone: String? = null,
    val memo: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CustomerMemoPhotoUploadUrlRequest(
    @SerialName("file_name") val fileName: String,
)

@Serializable
data class CustomerMemoPhotoUploadUrlResponse(
    @SerialName("photo_id") val photoId: String,
    @SerialName("memo_id") val memoId: String,
    @SerialName("s3_key") val s3Key: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("upload_headers") val uploadHeaders: Map<String, String> = emptyMap(),
)

@Serializable
data class SaveCustomerMemoPhotoRequest(
    @SerialName("photo_id") val photoId: String? = null,
    @SerialName("s3_key") val s3Key: String,
    val caption: String? = null,
)

@Serializable
data class CustomerMemoPhotoResponse(
    val id: String,
    @SerialName("memo_id") val memoId: String? = null,
    val url: String? = null,
    val caption: String? = null,
)
