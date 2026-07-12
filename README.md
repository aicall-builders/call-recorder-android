# 📱 FIANO Android Client

> 통화요약 CRM FIANO의 안드로이드 앱 — 통화 녹음 자동 감지, AI 분석, 고객 히스토리 정리, 일정 추출·캘린더 등록

[🌐 웹 데모](https://dk1k75g0ji3vw.cloudfront.net) 

[📱 APK 빌드 산출물](app/build/outputs/apk/debug/app-debug.apk)

[📊 모니터링 대시보드](http://15.165.17.218:3000/public-dashboards/97b5462a12b54bf9b827b07eeee699f4)

[📖 Backend README](https://github.com/aicall-builders/ai-call-assistant)

---

## 제출 기준

최종 제출용 기준은 아래 커밋입니다.

| 항목 | 값 |
|---|---|
| 기준 브랜치 | `main` |
| 제출 정리 브랜치 | `release/final-submission-consent` |
| 기준 UI 브랜치 | `origin/codex/main-rollback-ui-merge` |
| 기준 UI 커밋 | `e0cb888 Unify popup styles and back navigation` |
| 동의서 반영 커밋 | `a30cc60 feat: require consent before login` |
| 패키지명 | `com.callrecorder.app` |
| API stage | `rebuild` |
| 검증 명령 | `./gradlew.bat :app:compileDebugKotlin`, `./gradlew.bat :app:assembleDebug` |

제출 APK는 아래 명령으로 재생성합니다.

```bash
./gradlew.bat :app:assembleDebug
```

생성 위치:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 🎯 역할

본 레포는 통화요약 CRM **FIANO**의 **안드로이드 클라이언트**를 담당합니다.

핵심 책임:
- 삼성 기본 통화녹음 파일 자동 감지 (ContentObserver + WorkManager 이중화)
- 자동 PII 분류 (BUSINESS / PERSONAL / UNCLASSIFIED)
- S3 직접 PUT 업로드 (presigned URL)
- 통화 상세 + 음성 재생 (ExoPlayer)
- 통화 내용 AI 분석 데이터 히스토리 정리
- 통화자별 고객 맥락 관리
- 일정 추출 후 캘린더 자동 등록
- 카카오 OAuth → Firebase Custom Token 인증
- Firebase Crashlytics 기반 crash/non-fatal 원격 진단
- 로그인 전 필수 약관 및 개인정보 국외이전 동의

> 통화 경로에는 일체 개입하지 않습니다. 통화녹음이 끝난 파일을 **사후에** 수집할 뿐이므로 통화 품질·부재중 알림에 영향 없음.

---

## 🛠️ 기술 스택

- **언어**: Kotlin 2.1.20
- **UI**: Jetpack Compose (BOM 2024.02) + Material 3
- **DI**: 수동 DI (`AppContainer`) — Hilt 없이 가볍게
- **네트워크**: Retrofit 2.9 + OkHttp 4.12 + Kotlinx Serialization
- **로컬 DB**: Room 2.7.2
- **저장소**: DataStore Preferences (토큰)
- **백그라운드**: WorkManager 2.9 (주기 스캔) + Foreground Service (실시간 감지)
- **인증**: Firebase Auth + Kakao SDK v2 + Google Sign-In
- **모니터링**: Firebase Crashlytics + Analytics
- **오디오 재생**: Media3 ExoPlayer 1.2.1
- **최소 SDK**: Android 8.0 (API 26) — 시장 점유율 99%+

---

## 📁 폴더 구조

```
app/src/main/java/com/callrecorder/app/
├── CallRecorderApp.kt          # Application + 초기화
├── MainActivity.kt              # 네비게이션 호스트
├── data/
│   ├── api/                     # Retrofit (ApiService, ApiClient)
│   ├── local/                   # Room (RecordingEntity), TokenStore
│   ├── model/                   # DTO
│   └── repository/              # Auth/Store/Call Repository
├── di/AppContainer.kt          # 의존성 컨테이너
├── service/
│   ├── RecordingObserverService.kt  # ContentObserver 포그라운드 서비스
│   └── BootReceiver.kt         # 부팅 시 재시작
├── worker/
│   ├── UploadWorker.kt          # 즉시 업로드
│   └── ScanAndUploadWorker.kt  # 15분 주기 스캔+업로드
├── ui/
│   ├── theme/                   # Material 3 테마
│   └── screens/                 # 로그인, 권한, 가게, 통화목록, 상세, 설정
└── util/RecordingScanner.kt    # 제조사별 녹음 폴더 + MediaStore 스캐너
```

---

## 🏗️ 핵심 아키텍처: 통화 감지 이중화

ContentObserver와 WorkManager를 **둘 다** 사용해서 누락을 최소화했습니다.

### 1차: ContentObserver (실시간)

`RecordingObserverService`가 포그라운드 서비스로 동작하면서 `MediaStore.Audio` 변경을 감지합니다. 통화 종료 직후 녹음 파일이 저장되는 순간 즉시 트리거됩니다.

- **2초 디바운스**: 녹음 버퍼 플러시 대기
- **최근 1시간 내 파일만 스캔**: 효율 극대화
- **감지 직후**: `UploadWorker.enqueueOneShot()` → 네트워크 연결 시 즉시 업로드

### 2차: 주기 워커 (백업)

`ScanAndUploadWorker`가 15분마다 실행됩니다. 옵저버가 OS에 의해 종료되었거나, 앱이 강제 종료된 동안의 녹음을 챙깁니다.

- **최근 7일 내 파일**을 스캔
- DB에 없으면 등록 후 업로드

### 부팅 시 자동 복구

`BootReceiver`가 `BOOT_COMPLETED`를 받아 옵저버 서비스와 주기 워커를 다시 등록합니다.

---

## 🛡️ 자동 PII 분류 (Privacy by Default)

`READ_CONTACTS` 권한 없이 파일명·발신자 텍스트만으로 통화를 자동 분류합니다. **개인 통화가 사용자 동의 없이 서버로 업로드되는 것을 차단**하는 핵심 장치입니다.

| 카테고리 | 판단 기준 | 업로드 정책 |
|---------|---------|----------|
| `BUSINESS` | 숫자/하이픈/공백/+/괄호만 (저장 안 된 번호) | 기본 업로드 |
| `PERSONAL` | 한글/영문 포함 (연락처 저장된 이름) | 기본 미업로드 — 사용자 명시 동의 시에만 |
| `UNCLASSIFIED` | 발신자 정보 없음 | 기본 업로드 (이후 사용자 분류) |

> 자동 분류 로직은 구현·동작 중이며, 사용자가 분류 결과를 직접 조정하는 설정 UI는 Phase 2로 예정되어 있습니다.

---

## ✅ 필수 동의 흐름

신규 사용자는 로그인 전 `PrivacyConsentScreen`에서 필수 동의 항목을 확인해야 합니다.

필수 항목:

- 서비스 이용약관
- 개인정보 수집·이용
- 통화 녹음 및 AI 요약 처리
- 개인정보 국외 이전

모든 필수 항목이 체크되어야 `동의하고 계속하기` 버튼이 활성화됩니다. 동의 완료 시 앱 로컬 SharedPreferences에 동의 시각(`required_consent_accepted_at`)을 기록하여 동일 기기에서 로그인 화면으로 진행할 수 있게 합니다.

동의 화면은 디자이너가 반영한 FIANO 온보딩 UI 톤을 유지하며, 약관 전문은 화면 내 스크롤 영역에서 확인할 수 있습니다.

---

## 🧭 Crashlytics 원격 진단

Firebase Crashlytics가 적용되어 앱 크래시와 주요 non-fatal 오류를 원격에서 확인할 수 있습니다.

수집 대상:

- 앱 크래시
- `SafeLog.w/e` 경고·오류
- API 400/403/500 실패
- 네트워크 실패
- 로그인/업로드/분석 요청 실패

확인 가능한 custom key:

- `last_log_level`
- `last_log_tag`
- `login_provider`
- `account_email`
- `app_version`
- `build_type`

Crashlytics 이벤트는 Firebase Console 반영까지 몇 분 지연될 수 있습니다.

---

## 📂 통화 녹음 파일 위치 (제조사별)

`RecordingScanner`는 다음 경로를 모두 훑고 MediaStore도 함께 쿼리합니다.

| 제조사 | 경로 |
|--------|------|
| 삼성 | `/Recordings/Call/`, `/Sounds/CallRecord/` |
| LG | `/CallRecord/` |
| 샤오미 | `/MIUI/sound_recorder/call_rec/` |
| 화웨이 | `/Sounds/CallRecord/` |
| Pixel | `/Recordings/` |

> Phase 1 공식 지원은 **삼성**입니다. 다른 제조사 경로는 베스트 에포트 대응.

---

## 🔁 멱등성 / 중복 방지

`RecordingEntity.filePath`에 UNIQUE 인덱스가 걸려 있어 같은 파일을 두 번 등록하지 않습니다. `Insert(OnConflictStrategy.IGNORE)`로 멱등 보장.

서버 측에서도 `s3_key` 중복 시 거부 + Redis `SET NX` 락으로 동시 업로드를 차단합니다. (`audio_hash` 기반 멱등성 검증은 Phase 2 강화 예정)

---

## 🔐 권한 흐름

1. 로그인 직후 `PermissionScreen`에서 안내
2. **Android 13+**: `READ_MEDIA_AUDIO` + `POST_NOTIFICATIONS`
3. **Android 12 이하**: `READ_EXTERNAL_STORAGE`
4. 배터리 최적화 제외 (선택, 권장)
5. `FOREGROUND_SERVICE_DATA_SYNC` (백그라운드 업로드)

---

## 🔌 백엔드 API 매핑

| 화면 액션 | API |
|---|---|
| 카카오 로그인 | `POST /auth/kakao` |
| 가게 목록 | `GET /stores` |
| 가게 추가 | `POST /stores` |
| 업로드 URL 발급 | `POST /calls/upload` |
| S3 업로드 | `PUT {presigned_url}` |
| STT/요약 시작 | `POST /calls/{id}/process` |
| 통화 목록 | `GET /calls?store_id=...` |
| 통화 상세 | `GET /calls/{id}` |
| 음성 재생 URL | `GET /calls/{id}/audio` |
| 요약 단독 조회 | `GET /summaries/{id}` |
| 서버 분석 취소 | `POST /calls/{id}/cancel` |
| 외부 캘린더 OAuth 시작 | `GET /calendar/connections/{provider}/authorize` |
| 외부 캘린더 OAuth 완료 | `POST /calendar/connections/oauth-code` |

전체 API 명세는 [백엔드 레포](https://github.com/aicall-builders/ai-call-assistant) 참조.

---

## 🚀 빌드

### 사전 준비

**1. 카카오 네이티브 앱 키**

[카카오 디벨로퍼스](https://developers.kakao.com)에서 앱 생성 후 **네이티브 앱 키**를 받아 `gradle.properties`에 추가합니다.

```properties
API_BASE_URL=https://dr5lvldy4h.execute-api.ap-northeast-2.amazonaws.com/rebuild/
KAKAO_NATIVE_APP_KEY=your_kakao_key
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
```

카카오 콘솔의 **플랫폼 → Android** 등록 필요:
- 패키지명: `com.callrecorder.app`
- 키 해시: 디버그/릴리즈 키스토어에서 추출

**2. Firebase**

Firebase 프로젝트에서 `google-services.json`을 받아 `app/` 폴더에 배치합니다.

필요 기능:

- Firebase Auth
- Firebase Analytics
- Firebase Crashlytics
- Firebase Cloud Messaging

**3. API Base URL**

`gradle.properties`의 `API_BASE_URL`은 제출 기준으로 아래 stage를 사용합니다.

```properties
API_BASE_URL=https://dr5lvldy4h.execute-api.ap-northeast-2.amazonaws.com/rebuild/
```

**4. Android SDK**

로컬 빌드 시 `local.properties`에 SDK 경로가 필요합니다.

```properties
sdk.dir=C:/Users/{username}/AppData/Local/Android/Sdk
```

> `gradle.properties`, `local.properties`, `app/google-services.json`은 로컬 설정 파일이며 Git에 커밋하지 않습니다.

### Android Studio (권장)

1. Android Studio (Hedgehog 2023.1.1 이상) 실행
2. **File → Open** → 프로젝트 폴더 선택
3. Gradle Sync 자동 진행
4. ▶ 버튼으로 디바이스에 설치

### 커맨드라인

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew installDebug
```

---

## 📈 향후 로드맵 (Phase 2)

- FCM 토큰 서버 등록 → 요약 완료 푸시
- 로그인 토큰 자동 refresh
- 가게별 색상/이모지 커스터마이징
- 통화 검색 / 필터 고도화 (날짜·상대방·키워드)
- Google Play 정식 배포 (현재 사이드로딩)

---

## 🔗 관련 저장소

| 저장소 | 설명 |
|--------|------|
| [ai-call-assistant](https://github.com/aicall-builders/ai-call-assistant) | 🐍 Backend (AWS Lambda) |
| [ai-call-assistant-web](https://github.com/aicall-builders/ai-call-assistant-web) | 🌐 Web (Next.js) |
| **이 저장소** (`call-recorder-android`) | 📱 Android (이 레포) |

---

## 📄 라이선스

부트캠프 학습 프로젝트입니다. 코드 참고·학습 목적의 열람은 자유이나, 본 서비스의 아키텍처·디자인·문서를 무단으로 상업적 목적에 재이용하지 않기를 부탁드립니다.

---

*문서 기준: Final submission Android build (2026.07.12)*
