package com.drdisagree.teledrive.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.drdisagree.teledrive.core.common.NotificationDestinations
import com.drdisagree.teledrive.core.files.PendingShare
import com.drdisagree.teledrive.domain.model.AppTheme
import com.drdisagree.teledrive.presentation.applock.LockScreen
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.LocalCompactLayout
import com.drdisagree.teledrive.presentation.platform.LocalAppVersion
import com.drdisagree.teledrive.presentation.platform.LocalUrlOpener
import com.drdisagree.teledrive.presentation.navigation.AppNavHost
import com.drdisagree.teledrive.presentation.navigation.BottomBarHeight
import com.drdisagree.teledrive.presentation.navigation.LocalBottomBarHeight
import com.drdisagree.teledrive.presentation.navigation.Route
import com.drdisagree.teledrive.presentation.navigation.TopLevelDestination
import com.drdisagree.teledrive.presentation.navigation.navigateOnce
import com.drdisagree.teledrive.presentation.theme.TeleDriveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.drdisagree.teledrive.presentation.components.UpdateDialog
import com.drdisagree.teledrive.presentation.components.SessionBrokenDialog

@Composable
fun TeleDriveApp(
    pendingShare: PendingShare,
    notificationDestination: String? = null,
    onDestinationHandled: () -> Unit = {},
    viewModel: AppViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppStopped()
                Lifecycle.Event.ON_START -> viewModel.onAppStarted()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val darkTheme = when (state.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val pendingUpdate by viewModel.pendingUpdate.collectAsStateWithLifecycle()
    val sessionBroken by viewModel.sessionBroken.collectAsStateWithLifecycle()
    val urlOpener = LocalUrlOpener.current

    TeleDriveTheme(darkTheme = darkTheme, dynamicColor = state.dynamicColor) {
        CompositionLocalProvider(LocalCompactLayout provides state.compactLayout) {
            when {
                state.loading -> LoadingState()
                state.locked -> LockScreen(onUnlocked = viewModel::unlock)
                else -> MainScaffold(
                    pendingShare = pendingShare,
                    onboardingComplete = state.onboardingComplete,
                    notificationDestination = notificationDestination,
                    onDestinationHandled = onDestinationHandled,
                    onUpdateRequested = { viewModel.checkForUpdate(force = true) },
                    driveMissing = viewModel.driveMissing
                )
            }
            if (sessionBroken) {
                SessionBrokenDialog(onSignInAgain = viewModel::resetSession)
            }
            pendingUpdate?.takeIf { !state.loading && !state.locked }?.let { release ->
                UpdateDialog(
                    currentVersion = LocalAppVersion.current,
                    onOpenUrl = urlOpener::open,
                    release = release,
                    onDownload = {
                        viewModel.dismissUpdate()
                        urlOpener.open(release.pageUrl)
                    },
                    onDismiss = viewModel::dismissUpdate
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    pendingShare: PendingShare,
    onboardingComplete: Boolean,
    notificationDestination: String?,
    onDestinationHandled: () -> Unit,
    onUpdateRequested: () -> Unit,
    driveMissing: Flow<Unit>
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevel = TopLevelDestination.entries.any { destination ->
        currentDestination?.hasRoute(destination.route::class) == true
    }
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val useRail = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    val startDestination: Route = if (onboardingComplete) Route.Home else Route.Onboarding

    LaunchedEffect(Unit) {
        driveMissing.collect {
            val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
                while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
                true
            } == true
            if (graphReady) navController.navigate(Route.Channels)
        }
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) return@LaunchedEffect
        val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
            while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
            true
        } == true
        if (!graphReady) return@LaunchedEffect
        if (navController.currentDestination?.hasRoute(Route.Onboarding::class) == true) {
            return@LaunchedEffect
        }
        navController.navigate(Route.Onboarding) { popUpTo(0) { inclusive = true } }
    }

    LaunchedEffect(notificationDestination, onboardingComplete) {
        val target = notificationDestination ?: return@LaunchedEffect
        if (!onboardingComplete) return@LaunchedEffect

        val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
            while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
            true
        } == true
        if (!graphReady) return@LaunchedEffect

        when (target) {
            NotificationDestinations.TRANSFERS -> navController.navigate(Route.Transfers)
            NotificationDestinations.FILES -> navController.navigate(Route.Files())
            NotificationDestinations.NOTE -> navController.navigate(
                Route.NoteEditor(sharedText = pendingShare.text.value)
            )

            NotificationDestinations.UPDATE -> onUpdateRequested()

            else -> Unit
        }
        onDestinationHandled()
    }

    if (useRail && isTopLevel) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentDestination?.hasRoute(destination.route::class) == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = { if (!selected) navigateTopLevel(navController, destination) },
                        icon = {
                            Icon(
                                if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
            AppNavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)),
                verticalTabMotion = true
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            CompositionLocalProvider(
                LocalBottomBarHeight provides BottomBarHeight
            ) {
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                )
            }
            var navSlots by remember { mutableStateOf(emptyMap<Int, DpRect>()) }
            val topLevelIndex = TopLevelDestination.entries.indexOfFirst { destination ->
                currentDestination?.hasRoute(destination.route::class) == true
            }
            var selectedNavIndex by remember { mutableIntStateOf(0) }
            LaunchedEffect(topLevelIndex) {
                if (topLevelIndex >= 0) selectedNavIndex = topLevelIndex
            }
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically(barSlideSpec()) { height -> height } +
                        fadeIn(tween(BAR_SLIDE_MS)),
                exit = slideOutVertically(barSlideSpec()) { height -> height } +
                        fadeOut(tween(BAR_SLIDE_MS)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FloatingNavigationBar(
                    selectedIndex = selectedNavIndex,
                    onNavigate = { navigateTopLevel(navController, it) },
                    slots = navSlots,
                    onSlotMeasured = { index, slot ->
                        if (navSlots[index] != slot) navSlots = navSlots + (index to slot)
                    }
                )
            }
        }
    }
}

/**
 * Floating bottom navigation. Every destination keeps its label; the selected
 * one also reveals its icon, and a single pill slides between them instead of
 * each item drawing its own indicator.
 */
@Composable
private fun FloatingNavigationBar(
    selectedIndex: Int,
    onNavigate: (TopLevelDestination) -> Unit,
    slots: Map<Int, DpRect>,
    onSlotMeasured: (Int, DpRect) -> Unit
) {
    val entries = TopLevelDestination.entries

    val density = LocalDensity.current
    val selectedSlot = slots[selectedIndex]

    val pillX by animateDpAsState(
        targetValue = selectedSlot?.left ?: 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pillX"
    )
    val pillWidth by animateDpAsState(
        targetValue = selectedSlot?.width ?: 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pillWidth"
    )

    Surface(
        shape = RoundedCornerShape(BAR_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = BAR_MARGIN, vertical = BAR_MARGIN)
    ) {
        Box(
            modifier = Modifier
                .height(BAR_HEIGHT)
                .padding(horizontal = BAR_PADDING),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selectedSlot != null) {
                Box(
                    modifier = Modifier
                        .offset(x = pillX)
                        .width(pillWidth)
                        .height(PILL_HEIGHT)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        )
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                entries.forEachIndexed { index, destination ->
                    NavigationPillItem(
                        destination = destination,
                        selected = index == selectedIndex,
                        onClick = { if (index != selectedIndex) onNavigate(destination) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val left = with(density) {
                                coordinates.positionInParent().x.toDp()
                            }
                            val width = with(density) { coordinates.size.width.toDp() }
                            onSlotMeasured(index, DpRect(left, width))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationPillItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navItemColor"
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = ITEM_PADDING, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) +
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = destination.selectedIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** One spec for both directions so the bar leaves exactly the way it arrives. */
private fun barSlideSpec() = tween<IntOffset>(
    durationMillis = BAR_SLIDE_MS,
    easing = FastOutSlowInEasing
)

/** Measured position of one navigation item inside the bar. */
private data class DpRect(val left: Dp, val width: Dp)

private val BAR_CORNER = 32.dp
private val BAR_MARGIN = 12.dp
private val BAR_HEIGHT = 64.dp
private val PILL_HEIGHT = 44.dp
private val BAR_PADDING = (BAR_HEIGHT - PILL_HEIGHT) / 2
private val ITEM_PADDING = 12.dp

private fun navigateTopLevel(
    navController: NavHostController,
    destination: TopLevelDestination
) {
    navController.navigateOnce(destination.route) {
        popUpTo(Route.Home) { inclusive = destination.route == Route.Home }
        launchSingleTop = true
    }
}

private const val BAR_SLIDE_MS = 450

private const val NAV_READY_TIMEOUT_MS = 5_000L
private const val NAV_READY_POLL_MS = 50L
