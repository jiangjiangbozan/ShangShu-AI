package com.example.aiagent.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aiagent.core.storage.AuthState
import com.example.aiagent.core.storage.SharedContentManager
import com.example.aiagent.data.model.UserInfo
import com.example.aiagent.ui.auth.AuthViewModel
import com.example.aiagent.ui.auth.LoginScreen
import com.example.aiagent.ui.auth.RegisterScreen
import com.example.aiagent.ui.chat.ChatScreen
import com.example.aiagent.ui.chat.ChatViewModel
import com.example.aiagent.ui.agent.AgentScreen
import com.example.aiagent.ui.agent.AgentViewModel
import com.example.aiagent.ui.knowledge.KnowledgeScreen
import com.example.aiagent.ui.knowledge.KnowledgeViewModel
import com.example.aiagent.ui.customerservice.CustomerServiceScreen
import com.example.aiagent.ui.customerservice.CustomerServiceViewModel
import com.example.aiagent.ui.dataanalysis.DataAnalysisScreen
import com.example.aiagent.ui.memory.MemoryScreen
import com.example.aiagent.ui.character.CharacterScreen
import com.example.aiagent.ui.skillmarket.SkillMarketScreen
import com.example.aiagent.ui.livetranslate.LiveTranslateScreen
import com.example.aiagent.ui.profile.ProfileScreen
import com.example.aiagent.ui.screens.PlaceholderScreen

private const val CS_PERMISSION = "cs:session:list"
private const val DATA_ANALYSIS_PERMISSION = "data:analysis:schema"
private const val SUPER_ADMIN_ROLE = "SUPER_ADMIN"

private data class BottomTab(
    val icon: ImageVector,
    val label: String,
    val route: String,
)

private val bottomTabs = listOf(
    BottomTab(Icons.AutoMirrored.Filled.Chat, "对话", Screen.Chat.route),
    BottomTab(Icons.Filled.SmartToy, "任务", Screen.Agent.route),
    BottomTab(Icons.Filled.Folder, "知识库", Screen.Knowledge.route),
    BottomTab(Icons.Filled.SupportAgent, "客服", Screen.Service.route),
    BottomTab(Icons.Filled.Person, "我的", Screen.Profile.route),
)

private fun canAccessCustomerService(userInfo: UserInfo?): Boolean =
    userInfo?.permissions?.contains(CS_PERMISSION) == true ||
        userInfo?.roles?.contains(SUPER_ADMIN_ROLE) == true

private fun canAccessDataAnalysis(userInfo: UserInfo?): Boolean =
    userInfo?.permissions?.contains(DATA_ANALYSIS_PERMISSION) == true ||
        userInfo?.roles?.contains(SUPER_ADMIN_ROLE) == true

private fun visibleTabs(userInfo: UserInfo?): List<BottomTab> =
    bottomTabs.filter { tab ->
        tab.route != Screen.Service.route || canAccessCustomerService(userInfo)
    }

@Composable
fun AppRoot(
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory()),
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val sharedText by SharedContentManager.text.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Loading -> LoadingScreen()
        AuthState.LoggedOut -> AuthGraph(authViewModel)
        is AuthState.LoggedIn -> MainShell(
            userInfo = (authState as AuthState.LoggedIn).user,
            sharedText = sharedText,
            onSharedTextConsumed = SharedContentManager::consume,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthGraph(viewModel: AuthViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                viewModel = viewModel,
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onBackToLogin = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
    }
}

@Composable
fun MainShell(
    userInfo: UserInfo?,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                visibleTabs(userInfo).forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Screen.Chat.route) {
                val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory())
                ChatScreen(
                    viewModel = viewModel,
                    externalText = sharedText,
                    onExternalTextConsumed = onSharedTextConsumed,
                )
            }
            composable(Screen.Agent.route) {
                AgentScreen()
            }
            composable(Screen.Knowledge.route) {
                KnowledgeScreen()
            }
            composable(Screen.Service.route) {
                if (canAccessCustomerService(userInfo)) CustomerServiceScreen()
                else PlaceholderScreen("无权限访问", "当前账号没有客服工作台权限")
            }
            composable(Screen.Analysis.route) {
                if (canAccessDataAnalysis(userInfo)) DataAnalysisScreen()
                else PlaceholderScreen("无权限访问", "当前账号没有数据分析权限")
            }
            composable(Screen.Profile.route) {
                val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory())
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                    onNavigateToCharacters = { navController.navigate(Screen.Characters.route) },
                    onNavigateToSkillMarket = { navController.navigate(Screen.SkillMarket.route) },
                    onNavigateToLiveTranslate = { navController.navigate(Screen.LiveTranslate.route) },
                )
            }
            composable(Screen.Memory.route) {
                MemoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Characters.route) {
                CharacterScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SkillMarket.route) {
                SkillMarketScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.LiveTranslate.route) {
                LiveTranslateScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}