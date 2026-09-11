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
import com.example.aiagent.ui.profile.ProfileScreen
import com.example.aiagent.ui.screens.PlaceholderScreen

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Screen.Chat.route, "对话", Icons.AutoMirrored.Filled.Chat),
    BottomTab(Screen.Agent.route, "Agent", Icons.Filled.SmartToy),
    BottomTab(Screen.Knowledge.route, "知识库", Icons.Filled.Folder),
    BottomTab(Screen.Service.route, "客服", Icons.Filled.SupportAgent),
    BottomTab(Screen.Profile.route, "我的", Icons.Filled.Person),
)

@Composable
fun AppRoot(
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory()),
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Loading -> LoadingScreen()
        AuthState.LoggedOut -> AuthGraph(authViewModel)
        is AuthState.LoggedIn -> MainShell()
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
fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
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
                ChatScreen(viewModel = viewModel)
            }
            composable(Screen.Agent.route) {
                AgentScreen()
            }
            composable(Screen.Knowledge.route) {
                KnowledgeScreen()
            }
            composable(Screen.Service.route) {
                CustomerServiceScreen()
            }
            composable(Screen.Analysis.route) {
                DataAnalysisScreen()
            }
            composable(Screen.Profile.route) {
                val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory())
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                    onNavigateToCharacters = { navController.navigate(Screen.Characters.route) },
                    onNavigateToSkillMarket = { navController.navigate(Screen.SkillMarket.route) },
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
        }
    }
}