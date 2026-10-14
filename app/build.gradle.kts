plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hdlee73.dailyhabit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hdlee73.dailyhabit"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: rootProject.file("VERSION").readText().trim()
    }

    signingConfigs {
        // 고정된 디버그 키: 새 버전 APK를 기존 앱 위에 업데이트 설치할 수 있도록 저장소에 포함
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // 천주교인용(복음·기도) / 일반용(오늘의 명언) 두 가지 버전
    flavorDimensions += "edition"
    productFlavors {
        create("catholic") {
            dimension = "edition"
            // 기존 설치본이 그대로 업데이트되도록 원래 패키지 이름 유지
            applicationId = "com.hdlee73.dailyhabit"
            buildConfigField("boolean", "CATHOLIC", "true")
            resValue("string", "app_name", "DailyHabit 천주교인용")
        }
        create("general") {
            dimension = "edition"
            applicationId = "com.hdlee73.dailyhabit.general"
            buildConfigField("boolean", "CATHOLIC", "false")
            resValue("string", "app_name", "DailyHabit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // PC와 같은 와이파이 동기화: 짝 맺기 QR 코드 찍기
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // 동기화 합치기 규칙 테스트 (PC 앱과 같은 테스트를 JVM의 SQLite로 돌린다)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.xerial:sqlite-jdbc:3.47.1.0")
    testImplementation("org.json:json:20240303")
}
