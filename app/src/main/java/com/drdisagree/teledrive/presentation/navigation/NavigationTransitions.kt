package com.drdisagree.teledrive.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

object NavigationTransitions {

    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private const val ENTER_MS = 400
    private const val EXIT_MS = 200

    fun sharedAxisEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
        ) { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 } +
                fadeIn(tween(ENTER_MS / 2, delayMillis = ENTER_MS / 8))

    fun sharedAxisExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(EXIT_MS, easing = EmphasizedAccelerate)
        ) { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 } +
                fadeOut(tween(EXIT_MS))

    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(ENTER_MS / 2, delayMillis = EXIT_MS / 2)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
                )

    fun fadeThroughExit(): ExitTransition = fadeOut(tween(EXIT_MS / 2))

    fun tabEnterVertical(forward: Boolean): EnterTransition =
        slideInVertically(
            animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
        ) { fullHeight -> if (forward) fullHeight else -fullHeight } +
                fadeIn(tween(ENTER_MS / 2))

    fun tabExitVertical(forward: Boolean): ExitTransition =
        slideOutVertically(
            animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
        ) { fullHeight -> if (forward) -fullHeight else fullHeight } +
                fadeOut(tween(ENTER_MS / 2))

    fun tabEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
        ) { fullWidth -> if (forward) fullWidth else -fullWidth } +
                fadeIn(tween(ENTER_MS / 2))

    fun tabExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
        ) { fullWidth -> if (forward) -fullWidth else fullWidth } +
                fadeOut(tween(ENTER_MS / 2))

    fun previewEnter(): EnterTransition =
        fadeIn(tween(ENTER_MS / 2)) +
                scaleIn(
                    initialScale = 0.86f,
                    animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate)
                )

    fun previewExit(): ExitTransition =
        fadeOut(tween(EXIT_MS)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(EXIT_MS, easing = EmphasizedAccelerate)
                )

    fun AnimatedContentTransitionScope<NavBackStackEntry>.isPeerSwitch(): Boolean =
        initialState.isTopLevel() && targetState.isTopLevel()

    fun AnimatedContentTransitionScope<NavBackStackEntry>.movesForward(): Boolean {
        val from = initialState.topLevelIndex() ?: return true
        val to = targetState.topLevelIndex() ?: return true
        return to > from
    }

    private fun NavBackStackEntry.topLevelIndex(): Int? = TopLevelDestination.entries
        .indexOfFirst { destination.hasRoute(it.route::class) }
        .takeIf { it >= 0 }

    private fun NavBackStackEntry.isTopLevel(): Boolean {
        val current = destination
        return when {
            current.hasRoute(Route.Home::class) ||
                    current.hasRoute(Route.Gallery::class) ||
                    current.hasRoute(Route.Settings::class) -> true

            current.hasRoute(Route.Files::class) -> toRoute<Route.Files>().folderId == null
            else -> false
        }
    }
}
