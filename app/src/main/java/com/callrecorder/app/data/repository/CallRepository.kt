package com.callrecorder.app.data.repository


import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.RecordingDao
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.data.model.*
import com.callrecorder.app.util.SafeLog
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CallRepository(
    private val api: ApiService,
    private val dao: RecordingDao,
) {
    /** 새 녹음을 DB에 등록 (이미 있으면 ignore). 반환값은 로컬 PK(Long). */
    suspend fun registerLocal(rec: RecordingEntity): Long {
        dao.findByPath(rec.filePath)?.let { return it.id }
        return dao.insert(rec)
    }

    /** 모든 PENDING/FAILED 업로드 큐 (카테고리 무관) */
    suspend fun pendingUploads() = dao.pending()

    /** 특정 카테고리만 업로드 큐로 가져옴 (개인정보 보호 정책용) */
    suspend fun pendingUploadsByCategory(allowedCategories: List<String>) =
        dao.pendingByCategory(allowedCategories)

    /** 사용자가 수동으로 카테고리 변경 */
    suspend fun updateCategory(id: Long, category: String) =
        dao.updateCategory(id, category)

    /** 발신자 번호/이름 서버에 저장 (PATCH /calls/{id}) */
    suspend fun updateCaller(callId: String, number: String, name: String): Result<Unit> =
        runCatching {
            api.updateCall(callId, UpdateCallRequest(callerNumber = number, callerName = name))
            Unit
        }

    /** AI 요약문 사용자 수정 저장 (PATCH /calls/{id}) */
    suspend fun updateSummary(callId: String, summary: String): Result<Unit> =
        runCatching {
            val response = api.updateCall(callId, UpdateCallRequest(summary = summary))
            if (!response.isSuccessful) error("update summary failed: HTTP ${response.code()}")
            Unit
        }

    /** AI 요약 줄글 + 항목 키워드 저장 (PATCH /calls/{id}) */
    suspend fun updateSummaryAndKeywords(
        callId: String,
        summary: String?,
        internalKeywords: JsonObject,
    ): Result<Unit> =
        runCatching {
            val response = api.updateCall(
                callId,
                UpdateCallRequest(summary = summary, internalKeywords = internalKeywords),
            )
            if (!response.isSuccessful) error("update summary failed: HTTP ${response.code()}")
            Unit
        }

    /** 외부에서 명시적으로 FAILED 마킹 (예: 파일 삭제됨) */
    suspend fun markAsFailed(id: Long, reason: String) {
        dao.setError(id, RecordingStatus.FAILED, reason)
    }

    /** 업로드 큐에서 제거 (CANCELED). 폰 녹음 파일은 그대로 두고, 재스캔돼도 재업로드 안 함. */
    suspend fun cancelUpload(id: Long): Result<Unit> = runCatching {
        val rec = dao.findById(id)
        val shouldCancelServer = rec?.status == RecordingStatus.UPLOADED ||
                rec?.status == RecordingStatus.PROCESSING
        val callId = rec?.serverCallId?.takeIf { it.isNotBlank() && shouldCancelServer }
        if (callId != null) {
            val response = api.cancelCallProcessing(callId)
            if (!response.isSuccessful) {
                error("서버 분석 취소 실패: HTTP ${response.code()}")
            }
        }
        dao.updateStatus(id, RecordingStatus.CANCELED)
    }

    /** 진행 중인 업로드 전체 일괄 취소. */
    suspend fun cancelAllUploads(): Result<Unit> = runCatching {
        dao.getCancelableServerUploads().forEach { rec ->
            val callId = rec.serverCallId?.takeIf { it.isNotBlank() } ?: return@forEach
            val response = api.cancelCallProcessing(callId)
            if (!response.isSuccessful) {
                error("서버 분석 취소 실패: HTTP ${response.code()}")
            }
        }
        dao.cancelAllActive()
    }

    fun observeAll() = dao.observeAll()

    /** 카테고리별 조회 (UI 탭용) */
    fun observeByCategory(category: String) = dao.observeByCategory(category)
    fun observeCountByCategory(category: String) = dao.observeCountByCategory(category)

    /** 한 건 업로드 → 서버 처리 트리거까지. 반환값은 서버 callId(String, UUID). */
    suspend fun uploadAndProcess(
        rec: RecordingEntity,
        resolvedNumber: String? = null,
        resolvedName: String? = null,
    ): Result<String> = runCatching {
        val file = File(rec.filePath)
        require(file.exists()) { "파일이 사라졌습니다: ${rec.filePath}" }

        dao.updateStatus(rec.id, RecordingStatus.UPLOADING)

        // 1) Presigned URL 발급
        // ⚠️ 한글/이모지 파일명을 백엔드에 그대로 전송하면 S3 서명 검증 실패 발생.
        //    → 백엔드 전송용 파일명은 sanitize, 원본은 DB에만 보관.
        val mime = guessMime(file.extension)
        val safeFileName = sanitizeFileName(rec.fileName, rec.id)

        // caller_number에는 '진짜 전화번호'만 넣는다.
        //  - 1순위: CallLog 매칭 번호(resolvedNumber)
        //  - 2순위: 파일명 파싱값(rec.counterpartNumber)이 번호 형식일 때만
        //  - 둘 다 아니면 비움(빈 문자열) → 이름이 번호 자리에 들어가 고객이 쪼개지는 것 방지
        val phoneNumber = normalizedPhoneOrNull(resolvedNumber)
            ?: normalizedPhoneOrNull(rec.counterpartNumber)
            ?: ""

        // 이름: CallLog 이름 1순위, 없으면 파일명 파싱값이 '번호가 아닐 때'만 이름으로 사용
        val callerName = resolvedName?.takeIf { it.isNotBlank() }
            ?: rec.counterpartNumber?.takeIf { it.isNotBlank() && normalizedPhoneOrNull(it) == null }
            ?: ""

        val urlResp = api.requestUploadUrl(
            UploadUrlRequest(
                storeId = rec.storeId,
                fileName = safeFileName,                          // ← sanitize된 이름 전송
                fileSize = rec.fileSize,
                mimeType = mime,
                callStartedAt = isoFormat(rec.callStartedAtMillis),
                durationSeconds = rec.durationSeconds,
                counterpartNumber = phoneNumber,                  // ← 번호 형식만
                callerCategory = rec.category,
            )
        )

        // 2) S3 PUT 업로드
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        val headers = urlResp.uploadHeaders.filterValues { it.isNotBlank() }
        SafeLog.i("CallRepo", "📤 S3 PUT headers: $headers")
        SafeLog.i("CallRepo", "📤 S3 PUT mime: $mime, file: ${file.name}, size: ${file.length()}")
        val s3Resp = api.uploadToS3(urlResp.uploadUrl, headers, body)
        if (!s3Resp.isSuccessful) {
            error("S3 업로드 실패: HTTP ${s3Resp.code()}")
        }

        dao.setServerCallId(rec.id, urlResp.callId, RecordingStatus.UPLOADED)

        // 2.5) 통화기록 기반 발신자 번호/이름 보강 → 서버 저장 (폰·웹 양쪽 반영)
        if (phoneNumber.isNotBlank() || callerName.isNotBlank()) {
            runCatching {
                api.updateCall(
                    urlResp.callId,
                    UpdateCallRequest(
                        callerNumber = phoneNumber,
                        callerName = callerName,
                    ),
                )
            }.onFailure { SafeLog.w("CallRepo", "발신자 정보 보강 실패", it) }
        }

        // 3) STT/요약 처리 트리거. 트리거가 일시 실패해도 업로드는 성공했으므로
        // 로컬 항목은 PROCESSING으로 남겨 재시도/조회 흐름에서 이어가게 둔다.
        triggerProcess(urlResp.callId)
        dao.updateStatus(rec.id, RecordingStatus.PROCESSING)
        urlResp.callId
    }.onFailure { e ->
        dao.setError(rec.id, RecordingStatus.FAILED, e.message)
    }

    suspend fun triggerProcess(callId: String): Result<Unit> = runCatching {
        val procResp = api.processCall(callId)
        if (!procResp.isSuccessful) {
            error("process trigger failed: HTTP ${procResp.code()}")
        }
        Unit
    }.onFailure { e ->
        SafeLog.w("CallRepo", "process trigger failed for $callId: ${e.message}", e)
    }

    suspend fun deleteCall(callId: String): Result<Unit> = runCatching {
        val response = api.deleteCall(callId)
        if (!response.isSuccessful) {
            error("delete call failed: HTTP ${response.code()}")
        }
        dao.deleteByServerCallId(callId)
        Unit
    }

    suspend fun listCalls(storeId: String?): Result<List<Call>> = runCatching {
        api.listCalls(storeId).calls
    }

    suspend fun getDetail(callId: String): Result<CallDetail> = runCatching {
        api.getCall(callId)
    }

    /** 음성 재생용 presigned URL 가져오기. 백엔드가 안 줄 수도 있어 nullable. */
    suspend fun getAudioUrl(callId: String): Result<String?> = runCatching {
        api.getAudioUrl(callId).resolved
    }

    suspend fun getSummary(callId: String): Result<Summary> = runCatching {
        api.getSummary(callId)
    }

    private fun guessMime(ext: String) = when (ext.lowercase()) {
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "amr" -> "audio/amr"
        "3gp" -> "audio/3gpp"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }

    /**
     * S3/AWS 호환 안전한 파일명으로 변환.
     * - 한글, 이모지, 특수문자 모두 제거
     * - 원본 확장자 보존
     * - 로컬 PK(id)와 timestamp로 유일성 보장
     *
     * 예: "통화 녹음 💕내애기💕_260504_123708.m4a"
     *  → "rec_42_1714800123456.m4a"
     */
    private fun sanitizeFileName(originalName: String, recordId: Long): String {
        val ext = originalName.substringAfterLast('.', "m4a")
            .lowercase()
            .takeIf { it.length in 2..5 && it.matches(Regex("^[a-z0-9]+$")) }
            ?: "m4a"
        return "rec_${recordId}_${System.currentTimeMillis()}.$ext"
    }

    private fun isoFormat(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    /**
     * 전화번호 형식인 값만 통과시킨다.
     * 파일명에서 파싱된 값(예: "형_260617", "💕내애기💕")이 caller_number에
     * 들어가 같은 고객이 쪼개지는 문제를 막기 위함.
     * 숫자/하이픈/공백/+ 만으로 이뤄지고 숫자가 7자리 이상이면 번호로 본다.
     */
    private fun normalizedPhoneOrNull(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return null                 // 번호로 보기엔 숫자가 너무 적음
        if (raw.any { !(it.isDigit() || it in "-+. ()") }) return null  // 한글/이모지 등 섞이면 번호 아님
        return digits                                      // 하이픈 등 제거한 순수 숫자로 정규화
    }
}
