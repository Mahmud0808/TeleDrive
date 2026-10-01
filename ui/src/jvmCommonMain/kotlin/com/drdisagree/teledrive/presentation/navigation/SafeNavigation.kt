package com.drdisagree.teledrive.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Compose keeps delivering taps during exit transitions, so a double tap would stack the
 * destination twice.
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
