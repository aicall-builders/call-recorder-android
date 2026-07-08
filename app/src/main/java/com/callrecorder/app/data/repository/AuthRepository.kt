package com.callrecorder.app.data.repository

import android.content.Context
import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.TokenStore
import com.callrecorder.app.data.model.GoogleLoginRequest
import com.callrecorder.app.data.model.KakaoLoginRequest
import com.callrecorder.app.data.model.NaverLoginRequest
import com.callrecorder.app.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import retrofit2.HttpException

class AuthRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    private fun Throwable.readableAuthError(): Throwable {
        if (this !is HttpException) return this
        val body = response()?.errorBody()?.string()?.take(500).orEmpty()
        val message = if (body.isNotBlank()) "HTTP ${code()}: $body" else "HTTP ${code()}"
        return IllegalStateException(message, this)
    }

    private suspend fun FirebaseAuth.requireCurrentIdToken(): String =
        currentUser?.getIdToken(true)?.await()?.token
            ?: throw IllegalStateException("Firebase ID token null")

    // ═══════════════════════════════════════════════
    // 카카오 로그인 (기존 유지)
    // ═══════════════════════════════════════════════
    suspend fun loginWithKakao(context: Context): Result<Unit> = runCatching {
        val kakaoToken = kakaoLogin(context)

        val kakaoUser = suspendCancellableCoroutine<com.kakao.sdk.user.model.User> { cont ->
            UserApiClient.instance.me { user, error ->
                if (error != null) cont.resumeWithException(error)
                else if (user != null) cont.resume(user)
                else cont.resumeWithException(IllegalStateException("kakao user null"))
            }
        }

        val resp = try {
            api.loginWithKakao(KakaoLoginRequest(
                providerAccessToken = kakaoToken.accessToken,
                providerUserId = kakaoUser.id?.toString(),
                email = kakaoUser.kakaoAccount?.email,
                nickname = kakaoUser.kakaoAccount?.profile?.nickname,
            ))
        } catch (e: Throwable) {
            throw e.readableAuthError()
        }
        val customToken = resp.customToken

        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signInWithCustomToken(customToken).await()
        val firebaseIdToken = firebaseAuth.requireCurrentIdToken()
        SafeLog.i("AuthRepo", "Kakao Firebase signed in. uid=${firebaseAuth.currentUser?.uid}")

        tokenStore.saveTokens(
            access = firebaseIdToken,
            refresh = resp.refreshToken,
            userId = resp.user?.id ?: resp.uid ?: "",
            nickname = resp.user?.nickname ?: resp.nickname ?: resp.name ?: "",
        )
        tokenStore.saveLoginProfile(
            provider = "kakao",
            email = resp.user?.email ?: kakaoUser.kakaoAccount?.email,
        )
    }

    // ═══════════════════════════════════════════════
    // 구글 로그인
    //
    // 흐름:
    // 1) LoginScreen에서 Google Sign-In → idToken 획득
    // 2) Firebase에 Google credential로 로그인
    // 3) Firebase에서 최신 access_token 가져오기
    // 4) access_token을 백엔드 /auth/google 에 전달
    // 5) 백엔드가 Firebase custom_token 발급
    // 6) Firebase signInWithCustomToken
    // ═══════════════════════════════════════════════
    suspend fun loginWithGoogle(idToken: String): Result<Unit> = runCatching {
        // 1) Firebase에 Google credential로 로그인
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signInWithCredential(credential).await()
        val googleEmail = firebaseAuth.currentUser?.email
        SafeLog.i("AuthRepo", "Google Firebase credential sign-in ok")

        // idToken을 그대로 백엔드에 전달
        // 백엔드의 _fetch_profile("google", token)은
        // openidconnect userinfo 엔드포인트를 호출하는데
        // idToken도 Bearer로 사용 가능함
        SafeLog.i("AuthRepo", "Google idToken → backend /auth/google 호출")

        val resp = try {
            api.loginWithGoogle(GoogleLoginRequest(
                providerAccessToken = idToken,
            ))
        } catch (e: Throwable) {
            throw e.readableAuthError()
        }
        val customToken = resp.customToken
        SafeLog.i("AuthRepo", "Google backend login ok uid=${resp.uid}")

        // 3) Firebase custom_token으로 재로그인 (백엔드 uid 동기화)
        firebaseAuth.signInWithCustomToken(customToken).await()
        val firebaseIdToken = firebaseAuth.requireCurrentIdToken()
        SafeLog.i("AuthRepo", "Google Firebase custom token sign-in ok uid=${firebaseAuth.currentUser?.uid}")

        // 4) TokenStore 저장
        tokenStore.saveTokens(
            access = firebaseIdToken,
            refresh = resp.refreshToken,
            userId = resp.user?.id ?: resp.uid ?: "",
            nickname = resp.user?.nickname ?: resp.nickname ?: resp.name ?: "",
        )
        tokenStore.saveLoginProfile(
            provider = "google",
            email = resp.user?.email ?: googleEmail,
        )
    }

    // ═══════════════════════════════════════════════
    // 네이버 로그인
    // ═══════════════════════════════════════════════
    suspend fun loginWithNaver(context: Context): Result<Unit> = runCatching {
        val naverToken = suspendCancellableCoroutine<String> { cont ->
            NaverIdLoginSDK.authenticate(context, object : OAuthLoginCallback {
                override fun onSuccess() {
                    val token = NaverIdLoginSDK.getAccessToken()
                    if (token != null) cont.resume(token)
                    else cont.resumeWithException(IllegalStateException("Naver token null"))
                }
                override fun onFailure(httpStatus: Int, message: String) {
                    cont.resumeWithException(IllegalStateException("Naver 로그인 실패: $message"))
                }
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(IllegalStateException("Naver 로그인 오류: $message"))
                }
            })
        }

        SafeLog.i("AuthRepo", "Naver accessToken 획득 완료")

        val resp = try {
            api.loginWithNaver(NaverLoginRequest(providerAccessToken = naverToken))
        } catch (e: Throwable) {
            throw e.readableAuthError()
        }
        val customToken = resp.customToken

        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signInWithCustomToken(customToken).await()
        val firebaseIdToken = firebaseAuth.requireCurrentIdToken()
        SafeLog.i("AuthRepo", "Naver Firebase signed in. uid=${firebaseAuth.currentUser?.uid}")

        tokenStore.saveTokens(
            access = firebaseIdToken,
            refresh = resp.refreshToken,
            userId = resp.user?.id ?: resp.uid ?: "",
            nickname = resp.user?.nickname ?: resp.nickname ?: resp.name ?: "",
        )
        tokenStore.saveLoginProfile(
            provider = "naver",
            email = resp.user?.email,
        )
    }

    // ═══════════════════════════════════════════════
    // 카카오 로그인 헬퍼
    // ═══════════════════════════════════════════════
    private suspend fun kakaoLogin(context: Context): OAuthToken =
        suspendCancellableCoroutine { cont ->
            val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                if (error != null) cont.resumeWithException(error)
                else if (token != null) cont.resume(token)
                else cont.resumeWithException(IllegalStateException("Kakao token null"))
            }
            val client = UserApiClient.instance
            if (client.isKakaoTalkLoginAvailable(context)) {
                client.loginWithKakaoTalk(context) { token, error ->
                    if (error != null) client.loginWithKakaoAccount(context, callback = callback)
                    else callback(token, null)
                }
            } else {
                client.loginWithKakaoAccount(context, callback = callback)
            }
        }

    // ═══════════════════════════════════════════════
    // 로그아웃
    // ═══════════════════════════════════════════════
    suspend fun logout() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                UserApiClient.instance.logout { _ -> cont.resume(Unit) }
            }
        }
        runCatching { NaverIdLoginSDK.logout() }
        tokenStore.clear()
    }
}
