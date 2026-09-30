package fr.alaedine.aesh.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.alaedine.aesh.R

/**
 * The five top-level sections reachable from [AeshBottomNavigationBar],
 * each pinned to its own [AeshDestination] at the root of the back stack
 * (see [AeshNavHost]'s `onTabSelected` wiring) rather than being pushed on
 * top of another screen. Kept separate from [AeshDestination] itself so
 * every tab screen only needs to know about this fixed set of five to
 * report taps and highlight the active one — [AeshNavHost] remains the
 * sole place that resolves a tab to an actual navigable [AeshDestination].
 */
enum class AeshBottomNavTab {
    Dashboard,
    Students,
    Schedule,
    EssReport,
    Settings,
}

/**
 * Bottom navigation bar shared by every top-level tab screen (dashboard,
 * students, schedule, ESS report, settings). Switching tabs swaps the
 * screen content while this bar stays put and highlights [currentTab] —
 * unlike the "pushed" detail/form screens reached from within a tab (e.g.
 * adding/editing a student), which keep their own back arrow and don't
 * show this bar at all.
 */
@Composable
fun AeshBottomNavigationBar(
    currentTab: AeshBottomNavTab,
    onTabSelected: (AeshBottomNavTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab == AeshBottomNavTab.Dashboard,
            onClick = { onTabSelected(AeshBottomNavTab.Dashboard) },
            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = null) },
            label = { Text(text = stringResource(R.string.nav_label_dashboard)) },
        )
        NavigationBarItem(
            selected = currentTab == AeshBottomNavTab.Students,
            onClick = { onTabSelected(AeshBottomNavTab.Students) },
            icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
            label = { Text(text = stringResource(R.string.nav_label_students)) },
        )
        NavigationBarItem(
            selected = currentTab == AeshBottomNavTab.Schedule,
            onClick = { onTabSelected(AeshBottomNavTab.Schedule) },
            icon = { Icon(painter = painterResource(id = R.drawable.ic_schedule), contentDescription = null) },
            label = { Text(text = stringResource(R.string.nav_label_schedule)) },
        )
        NavigationBarItem(
            selected = currentTab == AeshBottomNavTab.EssReport,
            onClick = { onTabSelected(AeshBottomNavTab.EssReport) },
            icon = { Icon(imageVector = Icons.Default.Create, contentDescription = null) },
            label = { Text(text = stringResource(R.string.nav_label_ess_report)) },
        )
        NavigationBarItem(
            selected = currentTab == AeshBottomNavTab.Settings,
            onClick = { onTabSelected(AeshBottomNavTab.Settings) },
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
            label = { Text(text = stringResource(R.string.nav_label_settings)) },
        )
    }
}
