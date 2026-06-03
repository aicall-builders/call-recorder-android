package com.callrecorder.app.data.repository

import android.content.Context
import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.TokenStore
import com.callrecorder.app.data.model.SocialLoginRequest
import com.callrecorder.app.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    /**
     * 카카오 로그인 -> 우리 서버에 access_token 전달 -> 서버가 Firebase custom_token 발급
     * -> Firebase Auth.signInWithCustomToken 으로 ID 토큰 획득 가능 상태로 만듦
     * -> TokenStore 에는 사용자 식별 정보만 저장 (실제 ID 토큰은 매 요청마다 Firebase에서 가져옴)
     *
     * 주의: 백엔드의 access_token 필드는 사실 Firebase **custom_token**임 (audience가 identitytoolkit).
     *      이 값을 그대로 Authorization 헤더에 넣으면 401이 나므로 반드시 ID 토큰으로 교환해야 한다.
     */
    suspend fun loginWithKakao(context: Context): Result<Unit> = runCatching {
        // 1) 카카오 SDK 로 OAuth 토큰 획득
        val kakaoToken = kakaoLogin(context)

        // 2) 우리 백엔드에 카카오 access_token 넘겨서 Firebase custom_token 받기
        val resp = api.loginWithKakao(SocialLoginRequest(providerAccessToken = kakaoToken.accessToken))
        val customToken = resp.resolvedCustomToken
        require(customToken.isNotBlank()) { "Firebase custom token이 응답에 없습니다." }

        // 3) Firebase Auth 로 custom_token -> ID Token 교환 (이때 Firebase 에 로그인됨)
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signInWithCustomToken(customToken).await()
        val uid = firebaseAuth.currentUser?.uid
        SafeLog.i("AuthRepo", "Firebase signed in. uid=$uid")

        // 4) 식별 정보 저장 (ID 토큰 자체는 매 요청마다 getIdToken() 으로 가져옴)
        //    access_token 필드에는 임시로 customToken 을 넣어두지만 실제 API 호출엔 사용 X.
        //    토큰 존재 여부 = "로그인 됨" 신호로만 사용.
        tokenStore.saveTokens(
            access = customToken,
            refresh = resp.refreshToken,
            userId = resp.user.id,
            nickname = resp.user.nickname,
        )
    }

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
                    if (error != null) {
                        // 카카오톡 로그인 실패 시 카카오계정으로 폴백
                        client.loginWithKakaoAccount(context, callback = callback)
                    } else callback(token, null)
                }
            } else {
                client.loginWithKakaoAccount(context, callback = callback)
            }
        }

    suspend fun logout() {
        // Firebase 로그아웃
        runCatching { FirebaseAuth.getInstance().signOut() }
        // 카카오 로그아웃
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                UserApiClient.instance.logout { _ -> cont.resume(Unit) }
            }
        }
        tokenStore.clear()
    }
}