package com.example.aiagent

import android.app.Application
import com.example.aiagent.core.network.NetworkModule
import com.example.aiagent.core.network.safeApiCall
import com.example.aiagent.core.storage.DraftManager
import com.example.aiagent.core.storage.SearchHistoryManager
import com.example.aiagent.core.storage.SessionManager
import com.example.aiagent.core.storage.WebSearchSettingsManager
import com.example.aiagent.data.remote.AuthApi

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 注入 token 验证器：冷启动时调用 /users/me 验证持久化 token 是否仍有效
        SessionManager.tokenValidator = {
            try {
                val api: AuthApi = NetworkModule.api()
                safeApiCall { api.me() }
            } catch (_: Exception) {
                null
            }
        }

        SessionManager.init(this)
        DraftManager.init(this)
        SearchHistoryManager.init(this)
        WebSearchSettingsManager.init(this)
    }
}
