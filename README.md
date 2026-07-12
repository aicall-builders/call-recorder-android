# FIANO Android

소상공인을 위한 AI 통화 요약 서비스 FIANO의 Android 앱입니다. 통화 녹음 파일을 감지해 업로드하고, 서버에서 처리된 STT·AI 요약·고객 히스토리·일정 정보를 앱에서 확인합니다.

## 주요 기능

- 통화 녹음 파일 자동 감지 및 업로드
- 통화 목록, 상세 요약, 음성 재생
- 고객별 통화 히스토리 관리
- 일정 추출 및 캘린더 연동
- 카카오/구글 로그인 기반 Firebase 인증
- 로그인 전 필수 약관 및 개인정보 국외이전 동의
- Firebase Crashlytics 기반 crash/non-fatal 원격 진단

## 기술 스택

- Kotlin 2.1.20
- Jetpack Compose, Material 3
- Retrofit, OkHttp, Kotlinx Serialization
- Room, DataStore, WorkManager
- Firebase Auth, Analytics, Crashlytics, Cloud Messaging
- Kakao SDK, Google Sign-In
- Media3 ExoPlayer

## 실행 및 빌드

필수 로컬 파일:

```text
gradle.properties
local.properties
app/google-services.json
```

`gradle.properties` 예시:

```properties
API_BASE_URL=https://dr5lvldy4h.execute-api.ap-northeast-2.amazonaws.com/rebuild/
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
```

`local.properties` 예시:

```properties
sdk.dir=C:/Users/{username}/AppData/Local/Android/Sdk
```

빌드:

```bash
./gradlew.bat :app:assembleDebug
```

APK 위치:

```text
app/build/outputs/apk/debug/app-debug.apk
```

검증:

```bash
./gradlew.bat :app:compileDebugKotlin
```

## 동의 화면

신규 사용자는 로그인 전 필수 동의 화면을 거칩니다.

- 서비스 이용약관
- 개인정보 수집·이용
- 통화 녹음 및 AI 요약 처리
- 개인정보 국외 이전

모든 필수 항목에 동의해야 로그인 화면으로 이동할 수 있습니다.

## 관련 저장소

- Backend: [aicall-builders/ai-call-assistant](https://github.com/aicall-builders/ai-call-assistant)
- Web: [aicall-builders/ai-call-assistant-web](https://github.com/aicall-builders/ai-call-assistant-web)
- Android: [aicall-builders/call-recorder-android](https://github.com/aicall-builders/call-recorder-android)
