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
import com.callrecorder.app.onboarding.BusinessTypeScreen
import com.callrecorder.app.onboarding.OnboardingScreen
import com.callrecorder.app.onboarding.hasSelectedDomain
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
        // 콜드 스타트용 권한 게이트: 권한 통과한 사용자는 바로 메인 (업종/온보딩 X)
        composable(Routes.PERMISSION) {
            PermissionRoute(nav = nav, route = Routes.PERMISSION, force = false)
        }
        // 로그인 흐름용 권한 화면: 항상 표시 → 통과 후 업종선택(처음만) 또는 온보딩
        composable(Routes.PERMISSION_SETUP) {
            PermissionRoute(nav = nav, route = Routes.PERMISSION_SETUP, force = true)
        }
        // 업종 선택 (처음 한 번만): 저장 후 온보딩으로
        composable(Routes.BUSINESS_TYPE) {
            BusinessTypeScreen(
                onDone = {
                    nav.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.BUSINESS_TYPE) { inclusive = true }
                    }
                },
            )
        }
        // 온보딩 5장 (매 로그인): 끝나면 메인
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
            MainScreen(
                onCallClick = { },
                onLoggedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                onChangeStore = { nav.navigate(Routes.STORES) },
            )
        }
    }
}

/**
 * 권한 화면 공통 진입부.
 * - force = false (콜드 스타트): 권한을 마쳤으면 바로 MAIN
 * - force = true  (로그인 흐름): 권한 후 → 업종 미선택이면 BUSINESS_TYPE, 선택했으면 ONBOARDING
 */
@Composable
private fun PermissionRoute(nav: NavHostController, route: String, force: Boolean) {
    val context = LocalContext.current
    val storeVm: StoreViewModel = viewModel()
    val skipPermission = remember { !force && context.hasCompletedPermissionOnboarding() }

    // 다음 목적지 (진입 시 1회 결정)
    val next = remember {
        when {
            !force -> Routes.MAIN
            context.hasSelectedDomain() -> Routes.ONBOARDING   // 이미 업종 고름 → 온보딩만
            else -> Routes.BUSINESS_TYPE                       // 처음 → 업종 선택부터
        }
    }

    fun goNext() {
        storeVm.ensureActiveStore {
            nav.navigate(next) { popUpTo(route) { inclusive = true } }
        }
    }

    if (skipPermission) {
        LaunchedEffect(Unit) { goNext() }
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
 * 권한 관련 공통 유틸
 * ───────────────────────────────────────────────────── */
private const val APP_PREFS = "app_prefs"
private const val KEY_PERMISSION_ONBOARDING_DONE = "permission_onboarding_done"

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
    const val INTRO = "intro"
    const val LOGIN = "login"
    const val KAKAO_LINKED = "kakao_linked"
    const val PERMISSION = "permission"             // 콜드 스타트용 (자동 통과 가능)
    const val PERMISSION_SETUP = "permission_setup" // 로그인 흐름용 (항상 표시)
    const val BUSINESS_TYPE = "business_type"       // 업종 선택 (처음 한 번만)
    const val ONBOARDING = "onboarding"             // 기능 소개 5장 (매 로그인)
    const val STORES = "stores"
    const val MAIN = "main"
    const val CALL_DETAIL = "call_detail"
}