package com.omnitracker.renp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnitracker.renp.R
import com.omnitracker.renp.feature.addentry.AddEntryRoot
import com.omnitracker.renp.feature.dashboard.DashboardRoot
import com.omnitracker.renp.feature.history.HistoryRoot
import com.omnitracker.renp.feature.history.MeasurementDetailRoot
import com.omnitracker.renp.feature.measure.MeasureRoot
import com.omnitracker.renp.feature.profile.ProfileRoot
import com.omnitracker.renp.feature.settings.SettingsRoot
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val label: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, DashboardRoute::class, Icons.Filled.Home, R.string.nav_home),
    TopLevelDestination(HistoryRoute(), HistoryRoute::class, Icons.AutoMirrored.Filled.ShowChart, R.string.nav_history),
    TopLevelDestination(SettingsRoute, SettingsRoute::class, Icons.Filled.Settings, R.string.nav_settings),
)

@Composable
fun AppNavHost(
    startDestination: Any,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { dest -> currentDestination?.hasRoute(dest.routeClass) == true }

    fun navigateTopLevel(route: Any, restore: Boolean = true) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = restore }
            launchSingleTop = true
            restoreState = restore
        }
    }

    Scaffold(
        // Each screen handles its own top/status-bar insets
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // Only the bottom bar padding is applied here; each screen has its own Scaffold/top bar.
        val bottomPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
        Box(
            Modifier
                .padding(bottomPadding)
                .consumeWindowInsets(bottomPadding),
        ) {
            NavHost(navController = navController, startDestination = startDestination) {
                composable<ProfileRoute> {
                    ProfileRoot(
                        onFinished = { isOnboarding ->
                            if (isOnboarding) {
                                navController.navigate(DashboardRoute) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<DashboardRoute> {
                    DashboardRoot(
                        onMeasure = { navController.navigate(MeasureRoute) },
                        onAddManual = { navController.navigate(AddEntryRoute) },
                        onMetricClick = { metric -> navigateTopLevel(HistoryRoute(metric.name), restore = false) },
                        onMeasurementClick = { id -> navController.navigate(MeasurementDetailRoute(id)) },
                    )
                }
                composable<HistoryRoute> {
                    HistoryRoot(
                        onMeasurementClick = { id -> navController.navigate(MeasurementDetailRoute(id)) },
                        onAddManual = { navController.navigate(AddEntryRoute) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsRoot(
                        onEditProfile = { navController.navigate(ProfileRoute(isOnboarding = false)) },
                    )
                }
                composable<MeasureRoute> {
                    MeasureRoot(
                        onBack = { navController.popBackStack() },
                        onShowMeasurement = { id ->
                            navController.navigate(MeasurementDetailRoute(id)) {
                                popUpTo<MeasureRoute> { inclusive = true }
                            }
                        },
                    )
                }
                composable<AddEntryRoute> {
                    AddEntryRoot(onBack = { navController.popBackStack() })
                }
                composable<MeasurementDetailRoute> {
                    MeasurementDetailRoot(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
