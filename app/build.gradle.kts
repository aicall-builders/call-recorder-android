import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gradleProps = Properties().apply {
    rootProject.file("gradle.properties").inputStream().use { load(it) }
}
val kakaoKey: String = gradleProps.getProperty("KAKAO_NATIVE_APP_KEY", "")
val apiBaseUrl: String = gradleProps.getProperty("API_BASE_URL", "")
val composeUiVersion = "1.11.0"

android {
    namespace = "com.callrecorder.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callrecorder.app"
        minSdk = 26          // Android 8.0 - 99%+ 시장 커버
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // 카카오 SDK 매니페스트 placeholder , 네이버
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoKey
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "NAVER_CLIENT_ID", "\"${gradleProps.getProperty("NAVER_CLIENT_ID", "")}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"${gradleProps.getProperty("NAVER_CLIENT_SECRET", "")}\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui:$composeUiVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeUiVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUiVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUiVersion")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Networking - Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room (로컬 DB - 업로드 상태 관리)
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // WorkManager (백그라운드 업로드 + 주기적 스캔)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (토큰 등 저장)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-crashlytics-ktx")  // 발표 후 추가 예정

    // Kakao Login
    implementation("com.kakao.sdk:v2-user:2.20.1")

    // Google Login
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Naver Login
    implementation("com.navercorp.nid:oauth:5.9.1")


    // Coil (이미지)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
     // EXIF (사진 회전 보정)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Media3 ExoPlayer (음성 재생용)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // Accompanist - Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
