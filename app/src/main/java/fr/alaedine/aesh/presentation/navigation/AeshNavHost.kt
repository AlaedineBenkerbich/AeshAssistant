package fr.alaedine.aesh.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.alaedine.aesh.presentation.home.HomeRoute
import fr.alaedine.aesh.presentation.settings.SettingsRoute

/**
 * Hosts every screen behind a single [androidx.navigation.NavController],
 * wiring the [AeshDestination] graph. This is the sole place that knows
 * about the full set of screens; each route composable only knows how to
 * reach its immediate neighbors via the callbacks passed to it.
 */
@Composable
fun AeshNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AeshDestination.Dashboard,
        modifier = modifier,
    ) {
        composable<AeshDestination.Dashboard> {
            HomeRoute(
                onNavigateToSettings = { navController.navigate(AeshDestination.Settings) },
            )
        }
        composable<AeshDestination.Settings> {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
