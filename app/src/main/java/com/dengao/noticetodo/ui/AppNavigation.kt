package com.dengao.noticetodo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.ui.edit.EditScreen
import com.dengao.noticetodo.ui.home.HomeScreen
import com.dengao.noticetodo.ui.onboarding.OnboardingScreen
import com.dengao.noticetodo.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val EDIT = "edit?taskId={taskId}"
    const val SETTINGS = "settings"

    fun edit(taskId: Long? = null): String = "edit?taskId=${taskId ?: -1L}"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    onboarded: Boolean,
    incoming: IncomingState
) {
    val navController = rememberNavController()

    LaunchedEffect(incoming.draft) {
        if (incoming.draft != null) {
            navController.navigate(Routes.edit()) { launchSingleTop = true }
        }
    }

    LaunchedEffect(incoming.openTaskId) {
        val id = incoming.openTaskId ?: return@LaunchedEffect
        navController.navigate(Routes.edit(id)) { launchSingleTop = true }
        incoming.openTaskId = null
    }

    NavHost(
        navController = navController,
        startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                container = container,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(navController = navController, container = container, incoming = incoming)
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            EditScreen(
                navController = navController,
                taskId = entry.arguments?.getLong("taskId") ?: -1L,
                container = container,
                incoming = incoming
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController, container = container, incoming = incoming)
        }
    }
}
