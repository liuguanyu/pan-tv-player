import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

// 从 gitignore 的 local.properties 读取三方开放平台凭据（百度网盘 OAuth / 高德地图）与本地签名配置，
// 经 buildConfigField 注入 BuildConfig；真实密钥永不进入版本控制系统。
// 缺省值为占位符，保持“未配置”语义（如 AmapGeocodingStrategy 据此跳过高德策略）。
val localProperties: Properties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

/** 读取 local.properties 中的键，缺失时回退到 [default]；转义反斜杠/引号以安全嵌入 Java 字符串字面量。 */
fun localProp(name: String, default: String): String =
    localProperties.getProperty(name, default)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

fun rawLocalProp(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.baidu.tv.player.kt"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.baidu.tv.player.kt"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.baidu.tv.player.kt.HiltTestRunner"

        // ABI 由 productFlavors 控制：arm64 为常规包，compat 额外包含 32 位 armeabi-v7a，
        // 便于 Android 9 Sony BRAVIA 等可能运行 32 位系统的真机现场兜底。

        // 三方开放平台凭据：值来自 local.properties（缺省占位符），注入 BuildConfig。
        // BaiduConfig 转发这些字段，详见 BaiduConfig.kt。
        buildConfigField("String", "BAIDU_APP_ID", "\"${localProp("baidu.app.id", "YOUR_APP_ID")}\"")
        buildConfigField("String", "BAIDU_APP_KEY", "\"${localProp("baidu.app.key", "YOUR_APP_KEY")}\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"${localProp("baidu.secret.key", "YOUR_SECRET_KEY")}\"")
        buildConfigField("String", "BAIDU_SIGN_KEY", "\"${localProp("baidu.sign.key", "YOUR_SIGN_KEY")}\"")
        buildConfigField("String", "AMAP_API_KEY", "\"${localProp("amap.api.key", "YOUR_AMAP_API_KEY")}\"")
    }

    signingConfigs {
        val releaseStoreFile = rawLocalProp("signing.store.file")
        val releaseStorePassword = rawLocalProp("signing.store.password")
        val releaseKeyAlias = rawLocalProp("signing.key.alias")
        val releaseKeyPassword = rawLocalProp("signing.key.password") ?: releaseStorePassword

        if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
            create("releaseLocal") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    flavorDimensions += "abi"
    productFlavors {
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("compat") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        // 纯 32 位包：Sony BRAVIA（Android 9，32 位用户态）专用，
        // 去掉 arm64 原生库，安装体积减半，缓解电视存储不足导致的安装失败。
        create("v7a") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
    }

    buildTypes {
        release {
            // Phase 7 (8.1)：启用 R8 full mode + 资源压缩。
            // android.enableR8.fullMode=true 在 gradle.properties 中声明。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("releaseLocal")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 保留混淆映射，便于线上 crash 符号化回溯。
            // mapping.txt 输出至 app/build/outputs/mapping/<variant>/mapping.txt。
        }
    }

    // Phase 7：lintVital 需下载 com.android.tools.lint:lint-gradle，离线环境无缓存时
    // 会阻断 assembleRelease。R8/混淆/资源压缩本身不依赖 lint，故禁用 release vital lint
    // 检查以保证离线构建可重复；完整 lint 在 CI（有网络）中执行。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        // 生成 BuildConfig 以承载三方 key（见 defaultConfig buildConfigField）。
        buildConfig = true
    }

    testOptions {
        unitTests {
            // 让 android.util.Log 等 Android 桩方法在纯 JVM 单测中返回默认值而非抛异常。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AndroidX basics
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Image loading
    implementation(libs.glide)
    implementation(libs.glide.okhttp3.integration)
    ksp(libs.glide.compiler)
    implementation(libs.androidx.palette)

    // Media3 + LibVLC(FFmpeg) 软解兜底
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.libvlc.all)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation)

    // Security
    implementation(libs.androidx.security.crypto)

    // QR Code
    implementation(libs.zxing.core)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)

    // Android testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.testing.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}

// Phase 7 (8.7)：JaCoCo 覆盖率配置见 jacoco.gradle.kts（按需 apply）。
// 运行：./gradlew -PenableJacoco testDebugUnitTest jacocoTestReport
// 报告：app/build/reports/jacoco/jacocoTestReport/html/index.html
//
// 离线环境无 org.jacoco:org.jacoco.agent 缓存，默认不 apply jacoco plugin
// （AGP 应用 jacoco plugin 后会强制 testDebugUnitTest 解析 jacocoAgent，离线失败）。
// 在有网络的环境通过 -PenableJacoco 触发 apply from("jacoco.gradle.kts")。
if (project.hasProperty("enableJacoco")) {
    apply(from = "jacoco.gradle.kts")
}
