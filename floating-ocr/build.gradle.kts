plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fingertipfuture.hub.floatingocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fingertipfuture.hub.floatingocr.beta"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.26"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // ML Kit 的 OCR 原生库约 41 MB；四个 ABI 全打进一个包会让下载量翻几倍。
    // 发布时生成 ABI 专用 APK，客户端只下载当前手机需要的架构。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
