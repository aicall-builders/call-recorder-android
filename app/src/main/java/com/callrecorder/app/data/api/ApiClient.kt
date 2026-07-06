package com.callrecorder.app.data.api

import android.util.Log
import com.callrecorder.app.BuildConfig
import com.callrecorder.app.data.local.TokenStore
import com.callrecorder.app.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(tokenStore: TokenStore): ApiService {
        // 매 API 호출 시 Firebase 에서 최신 ID Token 을 가져와 헤더에 붙인다.
        // Firebase SDK 가 만료 시 자동 갱신해주므로, 우리는 만료 관리를 신경쓰지 않아도 된다.
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()

            // S3 업로드 요청에는 Authorization 헤더를 붙이지 않는다 (서명 충돌 방지)
            val isS3 = original.url.host.contains("s3.")
                    || (original.url.host.endsWith(".amazonaws.com")
                    && original.url.encodedPath.contains("/")
                    && original.header("X-Amz-Date") != null)

            // /auth/kakao 같은 인증 발급 엔드포인트도 Authorization 불필요
            val isAuthEndpoint = original.url.encodedPath.contains("/auth/")

            val req = if (isS3 || isAuthEndpoint) {
                original
            } else {
                val idToken = runBlocking {
                    withTimeoutOrNull(8_000L) { fetchFirebaseIdToken() }
                }
                if (!idToken.isNullOrBlank()) {
                    original.newBuilder()
                        .addHeader("Authorization", "Bearer $idToken")
                        .build()
                } else original
            }
            chain.proceed(req)
        }

        // 401 응답 시 토큰 클리어 -> 다음 진입 시 로그인 화면
        val unauthorizedInterceptor = Interceptor { chain ->
            val resp = chain.proceed(chain.request())
            if (resp.code == 401 && !chain.request().url.host.contains("s3.")) {
                SafeLog.w("ApiClient", "401 received -> clearing tokens & Firebase signOut")
                runCatching { FirebaseAuth.getInstance().signOut() }
                runBlocking { tokenStore.clear() }
            }
            resp
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()
        Log.d("API_URL", "Base URL = ${BuildConfig.API_BASE_URL}")
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }

    /**
     * 현재 Firebase 사용자의 ID Token 을 비동기로 가져온다.
     * 만료된 경우 SDK 가 알아서 새 토큰을 발급해준다.
     * 로그인 안 됐으면 null 반환.
     */
    private suspend fun fetchFirebaseIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return runCatching {
            user.getIdToken(false).await().token
        }.onFailure {
            SafeLog.w("ApiClient", "getIdToken failed: ${it.message}")
        }.getOrNull()
    }
}
