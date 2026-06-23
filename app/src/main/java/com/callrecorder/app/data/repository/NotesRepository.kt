package com.callrecorder.app.data.repository

import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.model.CallNoteResponse
import com.callrecorder.app.data.model.CallPhoto
import com.callrecorder.app.data.model.PhotoUploadUrlRequest
import com.callrecorder.app.data.model.SavePhotoRequest
import com.callrecorder.app.data.model.UpdateNoteRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class NotesRepository(private val api: ApiService) {

    /** 메모 + 사진 조회 */
    suspend fun getNote(callId: String): Result<CallNoteResponse> = runCatching {
        api.getCallNote(callId)
    }

    /** 메모 저장/수정 */
    suspend fun updateMemo(callId: String, memo: String): Result<Unit> = runCatching {
        api.updateCallNote(callId, UpdateNoteRequest(memo = memo))
        Unit
    }

    /**
     * 사진 1장 업로드 — 3단계를 한 번에 처리.
     * 1) presigned URL 발급
     * 2) S3에 PUT 업로드 (이미지 바이트)
     * 3) DB에 URL 저장 → 저장된 CallPhoto 반환
     *
     * @param fileName  확장자 포함 (예: "photo_1718.jpg") — content-type 결정용
     * @param imageBytes  이미지 원본 바이트
     */
    suspend fun uploadPhoto(
        callId: String,
        fileName: String,
        imageBytes: ByteArray,
    ): Result<CallPhoto> = runCatching {
        // 1) presigned URL 발급
        val urlRes = api.requestPhotoUploadUrl(callId, PhotoUploadUrlRequest(fileName = fileName))

        // 2) S3 PUT 업로드
        val contentType = urlRes.uploadHeaders["Content-Type"] ?: "image/jpeg"
        val body: RequestBody = imageBytes.toRequestBody(contentType.toMediaTypeOrNull())
        val putRes = api.uploadToS3(
            url = urlRes.uploadUrl,
            headers = urlRes.uploadHeaders,
            body = body,
        )
        if (!putRes.isSuccessful) {
            throw IllegalStateException("S3 업로드 실패: HTTP ${putRes.code()}")
        }

        // 3) DB 저장
        val saveRes = api.saveCallPhoto(
            callId,
            SavePhotoRequest(photoId = urlRes.photoId, s3Key = urlRes.s3Key),
        )
        saveRes.photo ?: CallPhoto(id = urlRes.photoId, url = "")
    }

    /** 사진 삭제 */
    suspend fun deletePhoto(callId: String, photoId: String): Result<Unit> = runCatching {
        val res = api.deleteCallPhoto(callId, photoId)
        if (!res.isSuccessful) throw IllegalStateException("삭제 실패: HTTP ${res.code()}")
        Unit
    }
}