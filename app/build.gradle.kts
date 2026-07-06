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
val kakaoKey: String = (providers.gradleProperty("KAKAO_NATIVE_APP_KEY").orNull ?: gradleProps.getProperty("KAKAO_NATIVE_APP_KEY") ?: "").trim()
val apiBaseUrl: String = gradleProps.getProperty("API_BASE_URL", "")
val composeUiVersion = "1.11.0"

android {
    namespace = "com.callrecorder.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callrecorder.app"
        minSdk = 26          // Android 8.0 - 99%+ ?쒖옣 而ㅻ쾭
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // 移댁뭅??SDK 留ㅻ땲?섏뒪??placeholder , ?ㅼ씠踰?
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = "385c52a6aae0029d1eae4c0533071650"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"385c52a6aae0029d1eae4c0533071650\"")
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

    // Room (濡쒖뺄 DB - ?낅줈???곹깭 愿由?
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // WorkManager (諛깃렇?쇱슫???낅줈??+ 二쇨린???ㅼ틪)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (?좏겙 ?????
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-crashlytics-ktx")  // 諛쒗몴 ??異붽? ?덉젙

    // Kakao Login
    implementation("com.kakao.sdk:v2-user:2.20.1")

    // Google Login
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Naver Login
    implementation("com.navercorp.nid:oauth:5.9.1")


    // Coil (?대?吏)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
     // EXIF (?ъ쭊 ?뚯쟾 蹂댁젙)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Media3 ExoPlayer (?뚯꽦 ?ъ깮??
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // Accompanist - Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}


