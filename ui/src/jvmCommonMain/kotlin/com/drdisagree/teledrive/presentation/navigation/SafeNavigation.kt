package com.drdisagree.teledrive.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Compose keeps handing taps to a screen while its exit transition runs, so a
 * fast double tap fires the same destination twice and stacks it. Navigating
 * only from a resumed entry drops the extra taps.
 */
private val NavHostController.isReadyForNavigation: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
        ?: false

fun NavHostController.navigateOnce(route: Route) {
    if (isReadyForNavigation) navigate(route)
}

fun NavHostController.navigateOnce(route: Route, builder: NavOptionsBuilder.() -> Unit) {
    if (isReadyForNavigation) navigate(route, builder)
}

fun NavHostController.popBackStackOnce() {
    if (isReadyForNavigation) popBackStack()
}
