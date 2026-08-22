package com.drdisagree.teledrive.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.presentation.channels.ChannelsScreen
import com.drdisagree.teledrive.presentation.collection.CollectionScreen
import com.drdisagree.teledrive.presentation.files.FilesScreen
import com.drdisagree.teledrive.presentation.gallery.GalleryScreen
import com.drdisagree.teledrive.presentation.home.HomeScreen
import com.drdisagree.teledrive.presentation.navigation.NavigationTransitions.isPeerSwitch
import com.drdisagree.teledrive.presentation.navigation.NavigationTransitions.movesForward
import com.drdisagree.teledrive.presentation.note.NoteEditorScreen
import com.drdisagree.teledrive.presentation.onboarding.OnboardingScreen
import com.drdisagree.teledrive.presentation.preview.PreviewScreen
import com.drdisagree.teledrive.presentation.search.SearchScreen
import com.drdisagree.teledrive.presentation.settings.ExclusionsScreen
import com.drdisagree.teledrive.presentation.settings.SettingsScreen
import com.drdisagree.teledrive.presentation.settings.SettingsSectionScreen
import com.drdisagree.teledrive.presentation.settings.SettingsSectionType
import com.drdisagree.teledrive.presentation.transfers.TransfersScreen
import com.drdisagree.teledrive.presentation.trash.TrashScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Route,
    modifier: Modifier = Modifier,
    verticalTabMotion: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (isPeerSwitch()) tabEnterFor(verticalTabMotion, movesForward())
            else NavigationTransitions.sharedAxisEnter(forward = true)
        },
        exitTransition = {
            if (isPeerSwitch()) tabExitFor(verticalTabMotion, movesForward())
            else NavigationTransitions.sharedAxisExit(forward = true)
        },
        popEnterTransition = {
            if (isPeerSwitch()) tabEnterFor(verticalTabMotion, movesForward())
            else NavigationTransitions.sharedAxisEnter(forward = false)
        },
        popExitTransition = {
            if (isPeerSwitch()) tabExitFor(verticalTabMotion, movesForward())
            else NavigationTransitions.sharedAxisExit(forward = false)
        }
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinished = {
                    navController.navigateOnce(Route.Home) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Home> {
            UnderBottomBar {
                HomeScreen(
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenFolder = { navController.navigateOnce(Route.Files(it)) },
                    onOpenTransfers = { navController.navigateOnce(Route.Transfers) },
                    onOpenTrash = { navController.navigateOnce(Route.Trash) },
                    onOpenChannels = { navController.navigateOnce(Route.Channels) },
                    onOpenCollection = { navController.navigateOnce(Route.Collection(it.name)) },
                    onOpenBackupSettings = {
                        navController.navigateOnce(
                            Route.SettingsSection(SettingsSectionType.BACKUP.name)
                        )
                    }
                )
            }
        }
        composable<Route.Files> { entry ->
            UnderBottomBar {
                val isRoot = entry.toRoute<Route.Files>().folderId == null
                FilesScreen(
                    onOpenFolder = { navController.navigateOnce(Route.Files(it)) },
                    onOpenCrumb = { folderId ->
                        val route = Route.Files(folderId)
                        if (!navController.popBackStack(route, inclusive = false)) {
                            navController.navigateOnce(route)
                        }
                    },
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenSearch = { navController.navigateOnce(Route.Search) },
                    onNewNote = { folderId ->
                        navController.navigateOnce(Route.NoteEditor(folderId = folderId))
                    },
                    onEditNote = { fileId, title ->
                        navController.navigateOnce(
                            Route.NoteEditor(fileId = fileId, title = title)
                        )
                    },
                    onBack = if (isRoot) null else ({ navController.popBackStackOnce() })
                )
            }
        }
        composable<Route.Gallery> {
            UnderBottomBar {
                val unfiledAlbumName = stringResource(R.string.album_not_in_folder)
                GalleryScreen(
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenAlbum = { album ->
                        navController.navigateOnce(
                            Route.GalleryAlbum(album.folderId, album.name ?: unfiledAlbumName)
                        )
                    }
                )
            }
        }
        composable<Route.GalleryAlbum> {
            GalleryScreen(
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                },
                onBack = { navController.popBackStackOnce() }
            )
        }
        composable<Route.Preview>(
            enterTransition = { NavigationTransitions.previewEnter() },
            exitTransition = { NavigationTransitions.fadeThroughExit() },
            popEnterTransition = { NavigationTransitions.fadeThroughEnter() },
            popExitTransition = { NavigationTransitions.previewExit() }
        ) {
            PreviewScreen(
                onBack = { navController.popBackStackOnce() },
                onEditNote = { fileId, title ->
                    navController.navigateOnce(
                        Route.NoteEditor(fileId = fileId, title = title)
                    )
                }
            )
        }
        composable<Route.Transfers> {
            TransfersScreen(onBack = { navController.popBackStackOnce() })
        }
        composable<Route.Search> {
            SearchScreen(
                onBack = { navController.popBackStackOnce() },
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                },
                onOpenFolder = { navController.navigateOnce(Route.Files(it)) }
            )
        }
        composable<Route.Collection> {
            CollectionScreen(
                onBack = { navController.popBackStackOnce() },
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                }
            )
        }
        composable<Route.NoteEditor> {
            NoteEditorScreen(onBack = { navController.popBackStackOnce() })
        }
        composable<Route.Channels> {
            ChannelsScreen(onBack = { navController.popBackStackOnce() })
        }
        composable<Route.Trash> {
            TrashScreen(onBack = { navController.popBackStackOnce() })
        }
        composable<Route.Settings> {
            UnderBottomBar {
                SettingsScreen(
                    onBack = null,
                    onOpenSection = { section ->
                        navController.navigateOnce(Route.SettingsSection(section.name))
                    }
                )
            }
        }
        composable<Route.SettingsSection> { entry ->
            val route = entry.toRoute<Route.SettingsSection>()
            val section = SettingsSectionType.entries
                .firstOrNull { it.name == route.section }
                ?: SettingsSectionType.ACCOUNT
            SettingsSectionScreen(
                section = section,
                onOpenChannels = { navController.navigateOnce(Route.Channels) },
                onBack = { navController.popBackStackOnce() },
                onOpenExclusions = { navController.navigateOnce(Route.Exclusions) },
                onLoggedOut = {
                    navController.navigateOnce(Route.Onboarding) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Exclusions> {
            ExclusionsScreen(onBack = { navController.popBackStackOnce() })
        }
    }
}

/** The rail stacks destinations vertically, so switching tabs moves up or down. */
private fun tabEnterFor(vertical: Boolean, forward: Boolean) =
    if (vertical) NavigationTransitions.tabEnterVertical(forward)
    else NavigationTransitions.tabEnter(forward)

private fun tabExitFor(vertical: Boolean, forward: Boolean) =
    if (vertical) NavigationTransitions.tabExitVertical(forward)
    else NavigationTransitions.tabExit(forward)

/**
 * Screens that sit under the bottom bar reserve its height for their whole
 * lifetime. Reading it per screen rather than from the current destination
 * keeps an outgoing screen's padding steady while it animates away.
 */
@Composable
private fun UnderBottomBar(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBottomBarInset provides LocalBottomBarHeight.current,
        content = content
    )
}
