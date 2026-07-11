package com.callrecorder.app.data.api

import com.callrecorder.app.data.model.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // ===== Me (유저 도메인) =====
    @GET("me")
    suspend fun getMe(): MeResponse

    @PATCH("me")
    suspend fun updateDomain(@Body body: UpdateDomainRequest): Response<Unit>

    // ===== Auth =====
    @POST("auth/kakao")
    suspend fun loginWithKakao(@Body body: KakaoLoginRequest): AuthResponse

    // ===== Stores =====
    @POST("stores")
    suspend fun createStore(@Body body: CreateStoreRequest): Store

    @GET("stores")
    suspend fun listStores(): StoreList

    // ===== Calls =====
    @POST("calls/upload")
    suspend fun requestUploadUrl(@Body body: UploadUrlRequest): UploadUrlResponse

    /**
     * S3 Presigned URL에 PUT 업로드.
     * @Url 로 절대 URL 사용. 헤더는 서버에서 받은 그대로 전달.
     */
    @PUT
    suspend fun uploadToS3(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<Unit>

    @POST("calls/{id}/process")
    suspend fun processCall(
        @Path("id") callId: String,
        @Body body: ProcessCallRequest = ProcessCallRequest()
    ): Response<Unit>

    @GET("calls")
    suspend fun listCalls(
        @Query("store_id") storeId: String? = null,
        @Query("limit") limit: Int = 50
    ): CallList

    @GET("calls/{id}")
    suspend fun getCall(@Path("id") callId: String): CallDetail

    /** 발신자 번호/이름 수정 (서버 저장) */
    @PATCH("calls/{id}")
    suspend fun updateCall(
        @Path("id") callId: String,
        @Body req: UpdateCallRequest,
    ): Response<Unit>

    /**
     * 음성 파일 재생용 presigned URL 발급.
     * 백엔드 응답 예: {"url": "https://...s3..."}
     * (실제 엔드포인트가 다르면 이 메서드만 수정하면 됨)
     */
    @GET("calls/{id}/audio")
    suspend fun getAudioUrl(@Path("id") callId: String): AudioUrlResponse

    @GET("summaries/{id}")
    suspend fun getSummary(@Path("id") callId: String): Summary

    // ===== Calendar =====  ← 여기부터 추가
    @GET("calendar/connections")
    suspend fun getCalendarConnections(): CalendarConnectionList

    @GET("calendar/events")
    suspend fun getCalendarEvents(
        @Query("date") date: String? = null,
        @Query("limit") limit: Int = 10,
    ): CalendarEventsResponse

    @GET("calendar/events")
    suspend fun getCalendarEventsRange(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("limit") limit: Int = 100,
    ): CalendarEventsResponse

    @GET("calendar/connections/{provider}/authorize")
    suspend fun getCalendarOAuthUrl(
        @Path("provider") provider: String,
        @Query("redirect_uri") redirectUri: String,
        @Query("state") state: String
    ): CalendarOAuthUrlResponse

    @DELETE("calendar/connections/{provider}")
    suspend fun disconnectCalendar(@Path("provider") provider: String): Response<Unit>

    @POST("calendar/connections/oauth-code")
    suspend fun completeCalendarOAuth(@Body body: CalendarOAuthCodeRequest): CalendarConnectionWrapper

    @POST("calls/{id}/calendar-events")
    suspend fun addCalendarEvent(
        @Path("id") callId: String,
        @Body body: Map<String, String>
    ): CalendarEventResponse

    // ===== Notes / Photos =====
    @GET("calls/{id}/note")
    suspend fun getCallNote(@Path("id") callId: String): CallNoteResponse

    @PATCH("calls/{id}/note")
    suspend fun updateCallNote(
        @Path("id") callId: String,
        @Body body: UpdateNoteRequest,
    ): UpdateNoteResponse

    @POST("calls/{id}/photos/upload-url")
    suspend fun requestPhotoUploadUrl(
        @Path("id") callId: String,
        @Body body: PhotoUploadUrlRequest,
    ): PhotoUploadUrlResponse

    @POST("calls/{id}/photos")
    suspend fun saveCallPhoto(
        @Path("id") callId: String,
        @Body body: SavePhotoRequest,
    ): SavePhotoResponse

    @DELETE("calls/{id}")
    suspend fun deleteCall(@Path("id") callId: String): Response<Unit>

    @POST("calls/{id}/cancel")
    suspend fun cancelCallProcessing(@Path("id") callId: String): Response<Unit>

    @DELETE("calls/{id}/photos/{photoId}")
    suspend fun deleteCallPhoto(
        @Path("id") callId: String,
        @Path("photoId") photoId: String,
    ): Response<Unit>


    // ===== Naver Auth =====
    @POST("auth/naver")
    suspend fun loginWithNaver(@Body body: NaverLoginRequest): AuthResponse

    // ===== google auth =====
    @POST("auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): AuthResponse

    // ===== Keywords =====
    @GET("stores/{storeId}/keywords")
    suspend fun listKeywords(@Path("storeId") storeId: String): CustomKeywordList

    @POST("stores/{storeId}/keywords")
    suspend fun createKeyword(
        @Path("storeId") storeId: String,
        @Body body: CreateKeywordRequest,
    ): CustomKeyword

    @PATCH("stores/{storeId}/keywords/{keywordId}")
    suspend fun updateKeyword(
        @Path("storeId") storeId: String,
        @Path("keywordId") keywordId: String,
        @Body body: UpdateKeywordRequest,
    ): Response<Unit>

    @DELETE("stores/{storeId}/keywords/{keywordId}")
    suspend fun deleteKeyword(
        @Path("storeId") storeId: String,
        @Path("keywordId") keywordId: String,
    ): Response<Unit>

    // ── 고객 프로필 + AI 분석 ──
    // phone에 한글/이모지가 들어올 수 있어 @Path 자동 인코딩에 의존.
    @GET("customers/{phone}")
    suspend fun getCustomer(@Path("phone") phone: String): CustomerProfileResponse

    @PATCH("customers/{phone}")
    suspend fun updateCustomer(
        @Path("phone") phone: String,
        @Body body: UpdateCustomerRequest,
    ): Response<Unit>
}
