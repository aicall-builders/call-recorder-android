package com.callrecorder.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.callrecorder.app.ui.screens.AuthViewModel
import com.callrecorder.app.ui.screens.IntroScreen
import com.callrecorder.app.ui.screens.KakaoLinkedScreen
import com.callrecorder.app.ui.screens.LoginScreen
import com.callrecorder.app.ui.screens.LoginType
import com.callrecorder.app.ui.screens.MainScreen
import com.callrecorder.app.ui.screens.PermissionScreen
import com.callrecorder.app.ui.screens.StoreViewModel
import com.callrecorder.app.ui.screens.StoresScreen
import com.callrecorder.app.di.CalendarOAuthResult
import com.callrecorder.app.onboarding.OnboardingScreen
import com.callrecorder.app.ui.theme.CallRecorderTheme
import com.callrecorder.app.util.SafeLog
import com.kakao.sdk.common.util.Utility

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyHash = Utility.getKeyHash(this)
        SafeLog.d("KEY_HASH", "===== 카카오 키 해시: $keyHash =====")

        // 콜드 스타트 시에도 딥링크 처리
        handleCalendarOAuthDeepLink(intent)

        setContent {
            CallRecorderTheme { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCalendarOAuthDeepLink(intent)
    }

    /**
     * callrecorder://oauth/{provider}?code=...&state=... 형태의 외부 캘린더 OAuth 콜백을
     * 받아 브릿지로 전달한다. 실제 토큰 교환은 ExternalCalendarTab이 앱의 Firebase 토큰으로 수행.
     */
    private fun handleCalendarOAuthDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "callrecorder" || data.host != "oauth") return
        val provider = data.lastPathSegment ?: return
        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state") ?: ""
        CallRecorderApp.instance.container.calendarOAuthBridge.submit(
            CalendarOAuthResult(provider = provider, code = code, state = state)
        )
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val auth: AuthViewModel = viewModel()
    val token by auth.isLoggedIn.collectAsState(initial = null)
    val state by auth.state.collectAsState()

    // 콜드 스타트: 로그인 안 됐으면 INTRO, 로그인됐으면 권한 게이트(PERMISSION) → 자동 통과 시 바로 메인
    // (온보딩은 "로그인 흐름"에서만 보여줌 → 재로그인할 때마다 다시 뜸)
    val start = if (token.isNullOrBlank()) Routes.INTRO else Routes.PERMISSION

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.INTRO) {
            IntroScreen(
                onStart = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.INTRO) { inclusive = true } } },
                onSkip = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.INTRO) { inclusive = true } } },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { loginType ->
                    // 로그인 흐름에서는 권한 화면을 항상 보여준다 (PERMISSION_SETUP)
                    val destination = if (loginType == LoginType.KAKAO) Routes.KAKAO_LINKED else Routes.PERMISSION_SETUP
                    nav.navigate(destination) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.KAKAO_LINKED) {
            KakaoLinkedScreen(
                onContinue = { nav.navigate(Routes.PERMISSION_SETUP) { popUpTo(Routes.KAKAO_LINKED) { inclusive = true } } },
                onCancel = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
            )
        }
        // 콜드 스타트용 권한 게이트: 권한 통과한 사용자는 화면 없이 바로 메인 (온보딩 X)
        composable(Routes.PERMISSION) {
            PermissionRoute(nav = nav, route = Routes.PERMISSION, force = false)
        }
        // 로그인 흐름용 권한 화면: 항상 표시 → 통과 후 온보딩으로
        composable(Routes.PERMISSION_SETUP) {
            PermissionRoute(nav = nav, route = Routes.PERMISSION_SETUP, force = true)
        }
        // 온보딩 6장: 로그인+권한 직후에만 진입 → 끝나면 메인
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.STORES) {
            StoresScreen(
                onContinue = { nav.navigate(Routes.MAIN) { popUpTo(Routes.STORES) { inclusive = true } } },
            )
        }
        composable(Routes.MAIN) {
            // onCallClick은 MainScreen 내부에서 처리
            MainScreen(
                onCallClick = { },  // 더 이상 사용 안 함 (MainScreen 내부에서 처리)
                onLoggedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                onChangeStore = { nav.navigate(Routes.STORES) },
            )
        }
    }
}

/**
 * 권한 화면 공통 진입부.
 * - force = false (콜드 스타트): 권한을 마쳤으면 화면 없이 바로 MAIN
 * - force = true  (로그인 흐름): 항상 권한 화면을 보여주고, 통과 후 ONBOARDING으로
 */
@Composable
private fun PermissionRoute(nav: NavHostController, route: String, force: Boolean) {
    val context = LocalContext.current
    val storeVm: StoreViewModel = viewModel()
    val skipPermission = remember { !force && context.hasCompletedPermissionOnboarding() }

    // 로그인 흐름이면 권한 후 온보딩, 콜드 스타트면 바로 메인
    val next = if (force) Routes.ONBOARDING else Routes.MAIN

    fun goNext() {
        storeVm.ensureActiveStore {
            nav.navigate(next) { popUpTo(route) { inclusive = true } }
        }
    }

    if (skipPermission) {
        LaunchedEffect(Unit) { goNext() }
        // ensureActiveStore 완료 전까지 잠깐 로딩 (빈 화면 깜빡임 방지)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PermissionScreen(
            onGranted = {
                context.markPermissionOnboardingDone()
                goNext()
            },
        )
    }
}

/* ─────────────────────────────────────────────────────
 * 권한 관련 공통 유틸 (런치 분기 + 설정 권한 화면에서 공용)
 * ───────────────────────────────────────────────────── */
private const val APP_PREFS = "app_prefs"
private const val KEY_PERMISSION_ONBOARDING_DONE = "permission_onboarding_done"

/**
 * 앱이 요구하는 권한 목록. (설정 → 권한 화면에서 토글로 노출)
 */
val REQUIRED_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)   // 통화기록 매칭
    add(Manifest.permission.READ_CONTACTS)   // caller_name
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

fun Context.hasAllRequiredPermissions(): Boolean =
    REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * 권한 온보딩 완료 여부.
 * - 권한 화면을 한 번이라도 통과하면 true 로 저장
 * - 기존 사용자(이미 녹음 권한 허용)는 완료한 것으로 간주
 */
fun Context.hasCompletedPermissionOnboarding(): Boolean {
    val prefs = getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_PERMISSION_ONBOARDING_DONE, false)) return true
    return ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.markPermissionOnboardingDone() {
    getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_PERMISSION_ONBOARDING_DONE, true)
        .apply()
}

object Routes {
    const val ONBOARDING = "onboarding"             // 로그인+권한 직후 온보딩 6장
    const val INTRO = "intro"
    const val LOGIN = "login"
    const val KAKAO_LINKED = "kakao_linked"
    const val PERMISSION = "permission"             // 콜드 스타트용 (자동 통과 가능)
    const val PERMISSION_SETUP = "permission_setup" // 로그인 흐름용 (항상 표시)
    const val STORES = "stores"
    const val MAIN = "main"
    const val CALL_DETAIL = "call_detail"
}