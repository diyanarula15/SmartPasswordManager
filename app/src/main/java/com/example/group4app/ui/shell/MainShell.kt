package com.example.group4app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.group4app.di.AppContainer
import com.example.group4app.navigation.MoreSubScreen
import com.example.group4app.navigation.ShellDestination
import com.example.group4app.navigation.ShellNavigator
import com.example.group4app.navigation.ShellTab
import com.legenddrry.feature.tools.AliasesRoute
import com.legenddrry.feature.tools.ApprovalsRoute
import com.legenddrry.feature.tools.SecurityRoute
import com.legenddrry.feature.tools.SettingsRoute
import com.legenddrry.feature.vault.EntriesRoute
import com.legenddrry.feature.vault.GenerateRoute

private data class TabSpec(
    val tab: ShellTab,
    val label: String,
    val tag: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val Tabs = listOf(
    TabSpec(ShellTab.VAULT, "Vault", AppTestTags.NAV_VAULT, Icons.Filled.Key, Icons.Outlined.Key),
    TabSpec(ShellTab.GENERATE, "Generate", AppTestTags.NAV_GENERATE, Icons.Filled.Password, Icons.Outlined.Password),
    TabSpec(ShellTab.APPROVALS, "Approvals", AppTestTags.NAV_APPROVALS, Icons.Filled.VerifiedUser, Icons.Outlined.VerifiedUser),
    TabSpec(ShellTab.ALIASES, "Aliases", AppTestTags.NAV_ALIASES, Icons.Filled.AlternateEmail, Icons.Outlined.AlternateEmail),
    TabSpec(ShellTab.MORE, "More", AppTestTags.NAV_MORE, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
)

/**
 * The unlocked app: one global NavigationBar and the current destination above it.
 * Navigation is plain saveable state reduced by [ShellNavigator]; each destination keeps its UI
 * state (scroll, fields) while you switch tabs via a SaveableStateHolder. All of it is dropped when
 * the vault locks, because [LegenDDRRYApp][com.example.group4app.navigation.LegenDDRRYApp] removes
 * this composable from the tree.
 */
@Composable
fun MainShell(container: AppContainer, modifier: Modifier = Modifier) {
    var destination by rememberSaveable(stateSaver = ShellNavigator.Saver) {
        mutableStateOf(ShellNavigator.Start)
    }
    val stateHolder = rememberSaveableStateHolder()

    val backTarget = ShellNavigator.back(destination)
    // Registered before the feature screens, so their own BackHandlers (dialogs, details) win.
    BackHandler(enabled = backTarget != null) {
        ShellNavigator.back(destination)?.let { destination = it }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(AppTestTags.MAIN_SHELL),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ShellNavigationBar(
                selected = destination.tab,
                onSelect = { destination = ShellNavigator.selectTab(destination, it) },
            )
        },
    ) { innerPadding ->
        // Content gets the area between the status bar and the NavigationBar. Consuming the
        // padding stops the feature Scaffolds/top bars from adding the same insets a second time.
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            stateHolder.SaveableStateProvider(destination.stateKey) {
                ShellDestinationContent(
                    destination = destination,
                    container = container,
                    onOpen = { destination = ShellNavigator.openSubScreen(destination, it) },
                    onBack = { ShellNavigator.back(destination)?.let { destination = it } },
                )
            }
        }
    }
}

@Composable
private fun ShellDestinationContent(
    destination: ShellDestination,
    container: AppContainer,
    onOpen: (MoreSubScreen) -> Unit,
    onBack: () -> Unit,
) {
    when (destination.tab) {
        ShellTab.VAULT -> EntriesRoute(container.vaultRepository)
        ShellTab.GENERATE -> GenerateRoute(container.vaultRepository)
        ShellTab.APPROVALS -> ApprovalsRoute()
        ShellTab.ALIASES -> AliasesRoute()
        ShellTab.MORE -> when (destination.subScreen) {
            null -> MoreScreen(onOpen = onOpen, onLockNow = { container.auth.lock() })
            MoreSubScreen.SECURITY -> SubScreen("Security dashboard", onBack) { SecurityRoute() }
            MoreSubScreen.SETTINGS -> SubScreen("Settings", onBack) { SettingsRoute() }
            MoreSubScreen.ABOUT -> SubScreen("About", onBack) { AboutScreen() }
        }
    }
}

@Composable
private fun ShellNavigationBar(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Tabs.forEach { spec ->
                val isSelected = spec.tab == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(spec.tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) spec.selectedIcon else spec.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(spec.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag(spec.tag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** Frame for screens opened from More: small top bar with a back arrow above the content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(AppTestTags.SUBSCREEN_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            content()
        }
    }
}
