import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
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

/**
 * 构建时自动探测宿主机局域网 IPv4（dev 环境默认后端地址使用）。
 *
 * 背景：dev 默认地址曾写死 10.143.187.138，DHCP 换 IP 后真机/模拟器直接连不上，且每次都要改代码。
 *
 * 探测顺序：
 * 1. Windows 默认路由所在网卡的 IPv4（最准：手机与电脑同网络时走的就是这张网卡）；
 * 2. 兜底：JVM 枚举网卡，优先无线（Windows 上 Java 报的是 wireless_xxxx 这类合成名），
 *    排除 WSL/vEthernet、VirtualBox NAT(10.0.2.x)、Docker、VPN 等手机侧不可达的网段。
 * 仍可用 -PAPI_BASE_URL=... / local.properties / API_BASE_URL 环境变量显式覆盖。
 */
fun detectLanIpv4ByOutbound(): String? =
    try {
        // UDP connect 只触发本机路由选路（不实际发包），拿到的 localAddress 即默认路由出口网卡 IP，
        // 也就是手机/模拟器同网络时应该访问的地址（WSL/VirtualBox/Docker 网卡天然被排除）
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("223.5.5.5"), 53)
            val ip = socket.localAddress?.hostAddress
            if (ip != null && !ip.startsWith("127.") && !ip.startsWith("0.") && !ip.startsWith("169.254.")) {
                ip
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }

fun detectLanIpv4ByNic(): String? {
    val excludedNicPrefixes =
        listOf("vethernet", "vmware", "virtualbox", "docker", "br-", "tailscale", "openvpn", "loopback")
    var best: Pair<Int, String>? = null
    val nics = Collections.list(NetworkInterface.getNetworkInterfaces())
    for (nic in nics) {
        if (!nic.isUp || nic.isLoopback) continue
        val nicName = nic.name.lowercase()
        if (excludedNicPrefixes.any { nicName.startsWith(it) }) continue
        for (addr in Collections.list(nic.inetAddresses)) {
            if (addr !is Inet4Address) continue
            if (!addr.isSiteLocalAddress) continue
            val ip = addr.hostAddress ?: continue
            // VirtualBox NAT 网段，手机侧无法回连
            if (ip.startsWith("10.0.2.")) continue
            var score = 0
            // Windows 上 Java 报的无线网卡名形如 wireless_32768，兼容常见写法
            if (listOf("wlan", "wi-fi", "wifi", "wireless").any { nicName.contains(it) }) score += 100
            if (ip.startsWith("192.168.")) score += 10
            val current = best
            if (current == null || score > current.first) best = score to ip
        }
    }
    return best?.second
}

fun detectLanIpv4(): String? {
    val byOutbound = detectLanIpv4ByOutbound()
    if (!byOutbound.isNullOrBlank()) return byOutbound
    return detectLanIpv4ByNic()
}

// 环境决定默认后端地址；prod/test 地址不写死在仓库，且 prod 强制 HTTPS
val appEnvironment = (buildSetting("APP_ENVIRONMENT", "APP_ENV") ?: "dev").lowercase()
val detectedDevHost = detectLanIpv4() ?: "127.0.0.1"
val environmentBaseUrls = mapOf(
    "dev" to "http://$detectedDevHost:8080/api/v1",
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
println("[android] APP_ENVIRONMENT=$appEnvironment, API_BASE_URL=$apiBaseUrl（dev 默认地址构建时自动探测宿主机局域网 IP）")

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
