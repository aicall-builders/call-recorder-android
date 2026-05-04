package com.callrecorder.app.data.repository

import android.util.Log
import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.RecordingDao
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
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

    /** 외부에서 명시적으로 FAILED 마킹 (예: 파일 삭제됨) */
    suspend fun markAsFailed(id: Long, reason: String) {
        dao.setError(id, RecordingStatus.FAILED, reason)
    }

    fun observeAll() = dao.observeAll()

    /** 카테고리별 조회 (UI 탭용) */
    fun observeByCategory(category: String) = dao.observeByCategory(category)
    fun observeCountByCategory(category: String) = dao.observeCountByCategory(category)

    /** 한 건 업로드 → 서버 처리 트리거까지. 반환값은 서버 callId(String, UUID). */
    suspend fun uploadAndProcess(rec: RecordingEntity): Result<String> = runCatching {
        val file = File(rec.filePath)
        require(file.exists()) { "파일이 사라졌습니다: ${rec.filePath}" }

        dao.updateStatus(rec.id, RecordingStatus.UPLOADING)

        // 1) Presigned URL 발급
        // ⚠️ 한글/이모지 파일명을 백엔드에 그대로 전송하면 S3 서명 검증 실패 발생.
        //    → 백엔드 전송용 파일명은 sanitize, 원본은 DB에만 보관.
        val mime = guessMime(file.extension)
        val safeFileName = sanitizeFileName(rec.fileName, rec.id)
        val urlResp = api.requestUploadUrl(
            UploadUrlRequest(
                storeId = rec.storeId,
                fileName = safeFileName,                          // ← sanitize된 이름 전송
                fileSize = rec.fileSize,
                mimeType = mime,
                callStartedAt = isoFormat(rec.callStartedAtMillis),
                durationSeconds = rec.durationSeconds,
                counterpartNumber = rec.counterpartNumber,
            )
        )

        // 2) S3 PUT 업로드
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        val headers = urlResp.uploadHeaders.toMutableMap()
        if (!headers.containsKey("Content-Type")) headers["Content-Type"] = mime
        val s3Resp = api.uploadToS3(urlResp.uploadUrl, headers, body)
        if (!s3Resp.isSuccessful) {
            error("S3 업로드 실패: HTTP ${s3Resp.code()}")
        }

        dao.setServerCallId(rec.id, urlResp.callId, RecordingStatus.UPLOADED)

        // 3) STT/요약 처리 트리거
        val procResp = api.processCall(urlResp.callId)
        if (!procResp.isSuccessful) {
            Log.w("CallRepo", "process trigger failed: ${procResp.code()}")
            // 실패해도 업로드는 됐으므로 PROCESSING 으로만 마킹
        }
        dao.updateStatus(rec.id, RecordingStatus.PROCESSING)
        urlResp.callId
    }.onFailure { e ->
        dao.setError(rec.id, RecordingStatus.FAILED, e.message)
    }

    suspend fun listCalls(storeId: String?): Result<List<Call>> = runCatching {
        api.listCalls(storeId).calls
    }

    suspend fun getDetail(callId: String): Result<CallDetail> = runCatching {
        api.getCall(callId)
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
}