package com.folio.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.folio.app.feature.editor.EditorScreen
import com.folio.app.feature.folder.FolderScreen
import com.folio.app.feature.home.HomeScreen
import com.folio.app.feature.settings.LicensesScreen
import com.folio.app.feature.settings.SettingsScreen
import com.folio.app.feature.viewer.SharedFilesScreen
import com.folio.app.feature.viewer.ViewerScreen
import com.folio.app.feature.viewer.ViewerViewModel

@Composable
fun FolioNavHost(
    startDestination: Any = Home,
    navController: NavHostController = rememberNavController(),
    /** Called when the user backs out of the start destination (ExternalOpenActivity finishes). */
    onExit: () -> Unit = {},
) {
    // Keep forward navigation subtle; use a fast accelerate-out when leaving a document so Back feels instant and snappy.
    val shift = with(LocalDensity.current) { 20.dp.roundToPx() }
    val enterSpec = tween<Float>(140, easing = FastOutSlowInEasing)
    val enterSlide = tween<IntOffset>(140, easing = FastOutSlowInEasing)
    val exitFade = tween<Float>(80, easing = LinearEasing)
    val popSlide = tween<IntOffset>(100, easing = FastOutLinearInEasing)
    fun back() { if (!navController.popBackStack()) onExit() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(enterSpec) + slideInHorizontally(enterSlide) { shift } },
        exitTransition = { fadeOut(exitFade) },
        popEnterTransition = { fadeIn(exitFade) },
        popExitTransition = { slideOutHorizontally(popSlide) { shift } + fadeOut(exitFade) },
    ) {
        composable<Home> {
            HomeScreen(
                onOpenFile = { uri -> navController.navigate(Viewer(uri)) },
                onEditFile = { uri -> navController.navigate(Editor(uri)) },
                onOpenFolder = { navController.navigate(it) },
                onOpenSettings = { navController.navigate(Settings) },
            )
        }
        composable<Folder> {
            FolderScreen(
                onBack = ::back,
                onGoToRoot = { root ->
                    navController.navigate(root) {
                        popUpTo<Home> { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenFolder = { navController.navigate(it) },
                onOpenFile = { uri -> navController.navigate(Viewer(uri)) },
                onFolderRemoved = { navController.popBackStack(Home, inclusive = false) },
            )
        }
        composable<Viewer> {
            ViewerScreen(onBack = ::back, onEdit = { navController.navigate(Editor(it)) }, onOpen = { navController.navigate(it) })
        }
        composable<Editor> {
            EditorScreen(
                onClose = ::back,
                onSaved = { navController.previousBackStackEntry?.savedStateHandle?.set(ViewerViewModel.RELOAD_KEY, System.currentTimeMillis()) },
            )
        }
        composable<SharedFiles> { entry -> SharedFilesScreen(entry.toRoute(), onBack = ::back, onOpen = { navController.navigate(it) }) }
        composable<Settings> { SettingsScreen(onBack = ::back, onOpenLicenses = { navController.navigate(Licenses) }) }
        composable<Licenses> { LicensesScreen(onBack = ::back) }
    }
}
