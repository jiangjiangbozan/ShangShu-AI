import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// local.properties 不入库，用于存放环境地址与正式签名密钥；CI 通过同名环境变量覆盖
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

/**
 * 读取构建配置，优先级：Gradle 属性 > local.properties > 环境变量。
 * - propertyName：Gradle 属性或 local.properties 中的键名
 * - environmentName：CI 使用的环境变量名
 */
val buildSetting: (String, String) -> String? = { propertyName, environmentName ->
    providers.gradleProperty(propertyName).orNull
        ?: localProperties.getProperty(propertyName)
        ?: System.getenv(environmentName)
}

// 环境决定默认后端地址；prod/test 地址不写死在仓库，且 prod 强制 HTTPS
val appEnvironment = (buildSetting("APP_ENVIRONMENT", "APP_ENV") ?: "dev").lowercase()
val environmentBaseUrls = mapOf(
    "dev" to "http://10.143.187.138:8080/api/v1",
    "prod" to "",
    "test" to "",
)
val defaultBaseUrl = environmentBaseUrls[appEnvironment]
    ?: throw GradleException("未知环境 $appEnvironment，仅支持 dev/test/prod")
val apiBaseUrl = buildSetting("API_BASE_URL", "API_BASE_URL")?.takeIf { it.isNotBlank() } ?: defaultBaseUrl
if (apiBaseUrl.isBlank()) {
    throw GradleException("$appEnvironment 环境必须通过 -PAPI_BASE_URL 或 API_BASE_URL 环境变量提供后端地址")
}
if (appEnvironment == "prod" && !apiBaseUrl.startsWith("https://", ignoreCase = true)) {
    throw GradleException("prod 环境禁止明文 HTTP，API_BASE_URL 必须使用 HTTPS")
}

// 正式签名密钥只从环境变量或本地属性读取，任何口令都不写入仓库
val releaseStoreFilePath = buildSetting("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
val releaseStorePassword = buildSetting("RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = buildSetting("RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = buildSetting("RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.aiagent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.aiagent"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "APP_ENVIRONMENT", "\"$appEnvironment\"")
        // 由 buildTypes 覆盖：debug 才允许明文 HTTP 与开发 IP 例外
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 开发包保留局域网 HTTP 后端访问，release 不继承这些例外
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // Keystore 支撑的加密存储：保护 token/CSRF/用户信息，避免明文落盘
    implementation(libs.androidx.security.crypto)

    // Network & Serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Markdown 渲染（AI 回答）
    implementation(libs.mikepenz.markdown)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.10")
    }
}
