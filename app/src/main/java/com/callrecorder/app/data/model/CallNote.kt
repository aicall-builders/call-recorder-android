package com.callrecorder.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 통화 사진 1장 */
@Serializable
data class CallPhoto(
    val id: String,
    val url: String,                              // presigned GET URL (조회용)
    @SerialName("created_at") val createdAt: String = "",
)

/** GET /calls/{id}/note 응답 — 메모 + 사진 목록 */
@Serializable
data class CallNoteResponse(
    @SerialName("call_id") val callId: String,
    val memo: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val photos: List<CallPhoto> = emptyList(),
    @SerialName("photo_count") val photoCount: Int = 0,
)

/** PATCH /calls/{id}/note 요청 */
@Serializable
data class UpdateNoteRequest(
    val memo: String,
)

/** PATCH /calls/{id}/note 응답 */
@Serializable
data class UpdateNoteResponse(
    val message: String = "",
    val memo: String = "",
)

/** POST /calls/{id}/photos/upload-url 요청 */
@Serializable
data class PhotoUploadUrlRequest(
    @SerialName("file_name") val fileName: String,
)

/** POST /calls/{id}/photos/upload-url 응답 */
@Serializable
data class PhotoUploadUrlResponse(
    @SerialName("photo_id") val photoId: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("s3_key") val s3Key: String,
    @SerialName("upload_headers") val uploadHeaders: Map<String, String> = emptyMap(),
)

/** POST /calls/{id}/photos 요청 — 업로드 완료 후 DB 저장 */
@Serializable
data class SavePhotoRequest(
    @SerialName("photo_id") val photoId: String,
    @SerialName("s3_key") val s3Key: String,
)

/** POST /calls/{id}/photos 응답 */
@Serializable
data class SavePhotoResponse(
    val message: String = "",
    val photo: CallPhoto? = null,
)