package com.example.aiagent.core.network

import com.example.aiagent.BuildConfig
import com.example.aiagent.core.storage.SessionManager
import com.example.aiagent.data.model.LoginResp
import com.example.aiagent.data.model.RefreshReq
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 网络基础设施：JSON 配置、OkHttp 客户端、Retrofit 实例及令牌刷新逻辑。
 *
 * 与 Web 端约定对齐：
 * - 请求头注入 X-Trace-Id、Authorization（Bearer）、X-CSRF-Token（非 GET）
 * - HTTP 401 触发 OkHttp Authenticator 刷新并重放；业务码 401 由上层 safeApiCall 处理
 */
object NetworkModule {

    /** CSRF Cookie 名，与后端 auth.csrf.cookie-name（application-dev.yml）保持一致 */
    private const val CSRF_COOKIE_NAME = "csrf-token"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 供 Retrofit 使用的转换工厂 */
    private val converterFactory = json.asConverterFactory(jsonMediaType)

    /**
     * 共享 CookieJar：后端 CSRF 采用 Double-Submit Cookie 校验（请求头 X-CSRF-Token 必须与
     * csrf-token Cookie 一致），无 CookieJar 时 Set-Cookie 被丢弃，登录/注册以外的
     * POST 全部 403。三个客户端共用同一实例，保证令牌刷新链路也能携带 Cookie。
     */
    private val cookieJar = SessionCookieJar()

    /** 刷新令牌所用的“裸”客户端（不含 Authenticator，避免递归） */
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(HeaderInterceptor())
            .authenticator(TokenAuthenticator)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
    }

    /**
     * SSE 流式请求专用客户端：不设读超时，避免长回答中途被切断。
     */
    val sseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(HeaderInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 获取指定 API 接口的实例。 */
    inline fun <reified T> api(): T = retrofit.create(T::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    /**
     * 会话级 CookieJar。
     *
     * 内存按 host 保存响应 Set-Cookie；冷启动后内存丢失时，用 SessionManager 持久化的
     * csrfToken 现场合成 csrf-token Cookie，保证「X-CSRF-Token 请求头 == csrf-token Cookie」
     * 的双提交校验在 App 重启后依然成立（两者始终取自同一份 token）。
     */
    private class SessionCookieJar : CookieJar {

        private val store = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isNotEmpty()) {
                store[url.host] = cookies
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = store[url.host].orEmpty().toMutableList()
            if (cookies.none { it.name == CSRF_COOKIE_NAME }) {
                SessionManager.csrfToken?.let { token ->
                    cookies += Cookie.Builder()
                        .name(CSRF_COOKIE_NAME)
                        .value(token)
                        .domain(url.host)
                        .path("/")
                        .build()
                }
            }
            return cookies
        }
    }

    /**
     * 请求头拦截器：注入追踪 ID 与认证信息。
     */
    private class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
                .header("X-Trace-Id", UUID.randomUUID().toString().replace("-", ""))

            SessionManager.accessToken?.let {
                builder.header("Authorization", "Bearer $it")
            }

            if (original.method != "GET") {
                SessionManager.csrfToken?.let {
                    builder.header("X-CSRF-Token", it)
                }
            }

            return chain.proceed(builder.build())
        }
    }

    /**
     * HTTP 401 自动刷新令牌并重放原请求。
     */
    private object TokenAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // 最多重试一次，避免无限循环
            if (responseCount(response) >= 2) {
                return null
            }
            val ok = refreshBlocking()
            if (!ok) return null

            val newAccess = SessionManager.accessToken ?: return null
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccess")
                .header("X-CSRF-Token", SessionManager.csrfToken ?: "")
                .build()
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prev = response.priorResponse
            while (prev != null) {
                count++
                prev = prev.priorResponse
            }
            return count
        }
    }

    /**
     * 阻塞式刷新令牌，供 Authenticator 使用。
     * @return 是否刷新成功
     */
    fun refreshBlocking(): Boolean {
        val refreshToken = SessionManager.refreshToken ?: return false
        val csrf = SessionManager.csrfToken
        val body = json.encodeToString(RefreshReq.serializer(), RefreshReq(refreshToken))
        val builder = Request.Builder()
            .url("${BuildConfig.API_BASE_URL.ensureTrailingSlash()}auth/refresh")
            .post(body.toRequestBody(jsonMediaType))
        csrf?.let { builder.header("X-CSRF-Token", it) }

        return try {
            refreshClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    SessionManager.clear()
                    return false
                }
                val raw = resp.body?.string().orEmpty()
                val result = json.decodeFromString<ApiResult<LoginResp>>(raw)
                val data = result.data
                if (result.code != 0 || data == null) {
                    SessionManager.clear()
                    return false
                }
                SessionManager.saveTokens(
                    access = data.accessToken,
                    refresh = data.refreshToken,
                    csrf = data.csrfToken,
                )
                true
            }
        } catch (e: Exception) {
            SessionManager.clear()
            false
        }
    }
}