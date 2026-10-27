package com.folio.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.folio.app.feature.folder.FolderScreen
import com.folio.app.feature.home.HomeScreen
import com.folio.app.feature.settings.LicensesScreen
import com.folio.app.feature.settings.SettingsScreen
import com.folio.app.feature.viewer.SharedFilesScreen
import com.folio.app.feature.viewer.ViewerScreen

@Composable
fun FolioNavHost(
    startDestination: Any = Home,
    navController: NavHostController = rememberNavController(),
    /** Called when the user backs out of the start destination (ExternalOpenActivity finishes). */
    onExit: () -> Unit = {},
) {
    // Plan §3.3: fade + 24dp slide, 220 ms.
    val shift = with(LocalDensity.current) { 24.dp.roundToPx() }
    val spec = tween<Float>(220, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(220, easing = FastOutSlowInEasing)
    fun back() { if (!navController.popBackStack()) onExit() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(spec) + slideInHorizontally(slide) { shift } },
        exitTransition = { fadeOut(spec) + slideOutHorizontally(slide) { -shift } },
        popEnterTransition = { fadeIn(spec) + slideInHorizontally(slide) { -shift } },
        popExitTransition = { fadeOut(spec) + slideOutHorizontally(slide) { shift } },
    ) {
        composable<Home> {
            HomeScreen(
                onOpenFile = { uri -> navController.navigate(Viewer(uri)) },
                onOpenFolder = { navController.navigate(it) },
                onOpenSettings = { navController.navigate(Settings) },
            )
        }
        composable<Folder> {
            FolderScreen(
                onBack = ::back,
                onOpenFolder = { navController.navigate(it) },
                onOpenFile = { uri -> navController.navigate(Viewer(uri)) },
                onFolderRemoved = { navController.popBackStack(Home, inclusive = false) },
            )
        }
        composable<Viewer> {
            ViewerScreen(onBack = ::back, onEdit = { navController.navigate(Editor(it)) }, onOpen = { navController.navigate(it) })
        }
        composable<SharedFiles> { entry -> SharedFilesScreen(entry.toRoute(), onBack = ::back, onOpen = { navController.navigate(it) }) }
        composable<Settings> { SettingsScreen(onBack = ::back, onOpenLicenses = { navController.navigate(Licenses) }) }
        composable<Licenses> { LicensesScreen(onBack = ::back) }
    }
}
