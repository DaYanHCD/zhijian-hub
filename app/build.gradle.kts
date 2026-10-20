import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 签名配置放在「密码与密钥」目录，与源码分离（铁律三：密钥不进仓库）。
// 该文件不存在时跳过 release 签名 —— 不影响 debug 构建与日常开发。
val keystorePropsFile = rootProject.file("../密码与密钥/安卓签名/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.fingertipfuture.hub"
    compileSdk = 36

    defaultConfig {
        // ⚠️ 与「指尖」不同的包名：两个 App 可以同时装在一台手机上
        //
        // 2026-10-16：这是**将来正式版**的包名 —— 现在还没发过正式版。
        // 对外只有一个渠道：测试版「指尖Hub-Beta」，包名 com.fingertipfuture.hub.beta
        // （见下面 buildTypes 里 debug / beta 两个构建的 applicationIdSuffix）。
        // 等功能彻底完善、要出正式版时，直接发 release 就是这个正式名字。
        applicationId = "com.fingertipfuture.hub"
        minSdk = 24
        targetSdk = 36
        // 2026-10-20：GitHub 开源版 1.0.0。
        // Beta 渠道由发布脚本在构建时临时覆盖版本号，不受这里的开源版本号影响。
        versionCode = 1
        versionName = "1.0.0"

        // 更新服务地址（必须以 / 结尾）。清单在 <根>/update/version.json。
        buildConfigField("String", "UPDATE_BASE_URL", "\"https://fingertipfuture.com/hub/\"")
        buildConfigField("boolean", "TEST_BUILD", "false")
        // 开源版不读取闭源渠道的更新清单；由下面的 open 构建覆盖为 true。
        buildConfigField("boolean", "OPEN_SOURCE_BUILD", "false")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file("../密码与密钥/安卓签名/${keystoreProps.getProperty("storeFile")}")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        /**
         * 日常开发用（模拟器 / Android Studio）：不压不混淆，能挂调试器、Compose 预览能用。
         * ⚠️ 这个包**不要发给手机**（9 MB，原因见下面的 beta）。
         *
         * 包名后缀和 beta 一样是 `.beta`（2026-10-16 之前叫 `.debug`）：现在整条产品线
         * 只有测试版，两者同包名 + 同（debug）签名 → 模拟器上的 debug 包和手机上的 beta 包
         * 互相覆盖安装时，设置 / 头像 / 对话都留着。桌面名字也一起改成「指尖Hub-Beta」。
         */
        debug {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-debug"
            // 测试通道：读 update/version-debug.json，正式版读 update/version.json
            buildConfigField("boolean", "TEST_BUILD", "true")
        }

        /**
         * 正式版：给「功能彻底完善之后」准备的，**现在不发**。
         * 名字「指尖Hub」+ 包名 `com.fingertipfuture.hub`（都不带后缀，见 defaultConfig）。
         */
        release {
            // 和「指尖」一样只打 arm，省体积
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        /**
         * GitHub 开源版：桌面名「指尖Hub-OS」，包名 `com.fingertipfuture.hub.os`。
         * 版本从 1.0.0 重新起算，并且不访问闭源 Beta / 正式版的更新清单。
         *
         * 独立的 `.os` 包名让它能和 Beta / 将来的正式版同时安装，也不会被错误提示
         * 「升级」到一个版本号更大的测试包。
         */
        create("open") {
            initWith(getByName("release"))
            applicationIdSuffix = ".os"
            buildConfigField("boolean", "OPEN_SOURCE_BUILD", "true")
        }

        /**
         * **现在唯一对外发出去的包**：桌面名字「指尖Hub-Beta」，
         * 包名 `com.fingertipfuture.hub.beta`（发布脚本 `-Channel beta` / 旧写法 `-Channel debug` 打的就是它）。
         *
         * 为什么不直接发 debug 包：debug 构建不走 R8、不缩资源，Compose 那套调试工具链
         * 还整个在里面 —— 同一份代码打出来 9 MB，压完只剩 1.3 MB
         * （2026-10-14 用户问「为什么安装包的体积这么大」）。
         * 测试渠道以前发的就是 debug 包，用户每更新一次要多下 8~9 MB。
         *
         * **包名后缀与 debug 相同、签名用 debug 密钥**，所以和 debug 包能互相原地覆盖升级，
         * 设置 / 头像 / 对话都还在。体积与优化则照正式包来。
         *
         * ⚠️ 2026-10-16 包名由 `.debug` 改成 `.beta`：老包（com.fingertipfuture.hub.debug）
         * 不会自动升级到这个包 —— 对手机来说这是「另一个 App」，要重新装一次。
         */
        create("beta") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            applicationIdSuffix = ".beta"
            // ⚠️ 刻意**不带**版本后缀：装到手机上「应用信息」里就是 1.0.00，
            // 和更新清单里的 versionName 一字不差（清单版本名对不上会让用户看到两个版本号）。
            // 「这是 Beta」由桌面名字「指尖Hub-Beta」表达。
            versionNameSuffix = ""
            signingConfig = signingConfigs.getByName("debug")
            // 测试通道：读 update/version-debug.json（正式版读 update/version.json）
            buildConfigField("boolean", "TEST_BUILD", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // App 内可运行时切换语言；App Bundle 不能按语言拆包，否则切换后可能缺资源。
    bundle {
        language {
            enableSplit = false
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
