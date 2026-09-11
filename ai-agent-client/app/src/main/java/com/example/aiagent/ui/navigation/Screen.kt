package com.example.aiagent.ui.navigation

/** 应用路由定义。 */
sealed class Screen(val route: String) {
    data object Login : Screen("auth/login")
    data object Register : Screen("auth/register")

    data object Chat : Screen("main/chat")
    data object Agent : Screen("main/agent")
    data object Knowledge : Screen("main/knowledge")
    data object Service : Screen("main/service")
    data object Analysis : Screen("main/analysis")
    data object Profile : Screen("main/profile")
    data object Memory : Screen("main/memory")
    data object Characters : Screen("main/characters")
    data object SkillMarket : Screen("main/skill-market")
}