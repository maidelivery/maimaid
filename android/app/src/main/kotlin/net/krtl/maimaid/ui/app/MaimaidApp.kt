package net.krtl.maimaid.ui.app

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import net.krtl.maimaid.R
import net.krtl.maimaid.feature.cloud.CloudAuthScreen
import net.krtl.maimaid.feature.imports.DataImportScreen
import net.krtl.maimaid.sync.StaticSyncScheduler
import net.krtl.maimaid.ui.community.CommunityAliasVotingBoardScreen
import net.krtl.maimaid.ui.home.DanDetailScreen
import net.krtl.maimaid.ui.home.DanListScreen
import net.krtl.maimaid.ui.home.HomeScreen
import net.krtl.maimaid.ui.home.UsefulLinksScreen
import net.krtl.maimaid.ui.navigation.AppRoute
import net.krtl.maimaid.ui.profile.ProfilesScreen
import net.krtl.maimaid.ui.random.RandomSongScreen
import net.krtl.maimaid.ui.scanner.ScannerScreen
import net.krtl.maimaid.ui.score.B50Screen
import net.krtl.maimaid.ui.score.PlateProgressScreen
import net.krtl.maimaid.ui.score.RecommendationsScreen
import net.krtl.maimaid.ui.score.ScoreListScreen
import net.krtl.maimaid.ui.search.SearchScreen
import net.krtl.maimaid.ui.settings.SettingsScreen
import net.krtl.maimaid.ui.settings.StaticSyncScreen
import net.krtl.maimaid.ui.song.SongDetailScreen
import net.krtl.maimaid.ui.song.SongMotionTokens
import net.krtl.maimaid.ui.song.SongSharedTransitionState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaimaidApp(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        container.staticDataRepository.observeSyncConfig().collect { config ->
            StaticSyncScheduler.schedule(context, config.backgroundSyncInterval)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val bottomRoutes = listOf(AppRoute.Home, AppRoute.Search, AppRoute.Scan, AppRoute.Settings)
    val bottomRouteOrder = bottomRoutes.mapIndexed { index, route -> route.route to index }.toMap()
    var isSearchBottomBarVisible by remember { mutableStateOf(true) }
    var activeSongTransitionState by remember { mutableStateOf<SongSharedTransitionState?>(null) }
    val isSearchRoute = currentDestination?.hierarchy?.any { it.route == AppRoute.Search.route } == true
    val openSongWithoutSharedTransition: (String) -> Unit = { songIdentifier ->
        activeSongTransitionState = null
        navController.navigate(AppRoute.SongDetail.create(songIdentifier))
    }

    LaunchedEffect(isSearchRoute) {
        if (!isSearchRoute) {
            isSearchBottomBarVisible = true
        }
    }

    LaunchedEffect(currentDestination?.route, activeSongTransitionState?.songIdentifier) {
        val isOnSongDetail = navController.currentBackStackEntry
            ?.destination
            ?.hierarchy
            ?.any { it.route == AppRoute.SongDetail.route } == true
        if (!isOnSongDetail && activeSongTransitionState != null) {
            delay(SongMotionTokens.RETURN_SETTLE_MILLIS.toLong())
            val stillOffDetail = navController.currentBackStackEntry
                ?.destination
                ?.hierarchy
                ?.none { it.route == AppRoute.SongDetail.route } == true
            if (stillOffDetail) {
                activeSongTransitionState = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (bottomRoutes.any { route -> currentDestination?.hierarchy?.any { it.route == route.route } == true }) {
                AnimatedVisibility(
                    visible = !isSearchRoute || isSearchBottomBarVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(180)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(160))
                ) {
                    NavigationBar {
                        bottomRoutes.forEach { route ->
                            val selected = currentDestination?.hierarchy?.any { it.route == route.route } == true
                            NavigationBarItem(
                                selected = selected,
                                enabled = true,
                                onClick = {
                                    navController.navigate(route.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (route) {
                                            AppRoute.Home -> Icons.Default.Home
                                            AppRoute.Search -> Icons.Default.Search
                                            AppRoute.Scan -> Icons.Default.PhotoCamera
                                            else -> Icons.Default.Settings
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = {
                                    Text(
                                        when (route) {
                                            AppRoute.Home -> stringResource(R.string.nav_home)
                                            AppRoute.Search -> stringResource(R.string.nav_search)
                                            AppRoute.Scan -> stringResource(R.string.nav_scan)
                                            else -> stringResource(R.string.nav_settings)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        SharedTransitionLayout {
            val openSongWithSharedTransition: (String, SongSharedTransitionState) -> Unit = { songIdentifier, transitionState ->
                activeSongTransitionState = transitionState
                navController.navigate(AppRoute.SongDetail.create(songIdentifier))
            }
            NavHost(
                navController = navController,
                startDestination = AppRoute.Home.route,
                enterTransition = {
                    val isSongDetailTransition =
                        initialState.destination.route == AppRoute.SongDetail.route ||
                            targetState.destination.route == AppRoute.SongDetail.route
                    val useSharedSongTransition = isSongDetailTransition && activeSongTransitionState != null
                    if (useSharedSongTransition) {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = SongMotionTokens.CONTAINER_DURATION_MILLIS
                            )
                        )
                    } else if (isSongDetailTransition) {
                        fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40))
                    } else {
                        val fromIndex = bottomRouteOrder[initialState.destination.route]
                        val toIndex = bottomRouteOrder[targetState.destination.route]
                        val direction = when {
                            fromIndex != null && toIndex != null && toIndex < fromIndex -> AnimatedContentTransitionScope.SlideDirection.End
                            else -> AnimatedContentTransitionScope.SlideDirection.Start
                        }
                        slideIntoContainer(
                            towards = direction,
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(220))
                    }
                },
                exitTransition = {
                    val isSongDetailTransition =
                        initialState.destination.route == AppRoute.SongDetail.route ||
                            targetState.destination.route == AppRoute.SongDetail.route
                    val useSharedSongTransition = isSongDetailTransition && activeSongTransitionState != null
                    if (useSharedSongTransition) {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = SongMotionTokens.CONTAINER_DURATION_MILLIS / 2
                            )
                        )
                    } else if (isSongDetailTransition) {
                        fadeOut(animationSpec = tween(durationMillis = 140))
                    } else {
                        val fromIndex = bottomRouteOrder[initialState.destination.route]
                        val toIndex = bottomRouteOrder[targetState.destination.route]
                        val direction = when {
                            fromIndex != null && toIndex != null && toIndex < fromIndex -> AnimatedContentTransitionScope.SlideDirection.End
                            else -> AnimatedContentTransitionScope.SlideDirection.Start
                        }
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(180))
                    }
                },
                popEnterTransition = {
                    val isSongDetailTransition =
                        initialState.destination.route == AppRoute.SongDetail.route ||
                            targetState.destination.route == AppRoute.SongDetail.route
                    val useSharedSongTransition = isSongDetailTransition && activeSongTransitionState != null
                    if (useSharedSongTransition) {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = SongMotionTokens.CONTAINER_DURATION_MILLIS
                            )
                        )
                    } else if (isSongDetailTransition) {
                        fadeIn(animationSpec = tween(durationMillis = 180))
                    } else {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(220))
                    }
                },
                popExitTransition = {
                    val isSongDetailTransition =
                        initialState.destination.route == AppRoute.SongDetail.route ||
                            targetState.destination.route == AppRoute.SongDetail.route
                    val useSharedSongTransition = isSongDetailTransition && activeSongTransitionState != null
                    if (useSharedSongTransition) {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = SongMotionTokens.CONTAINER_DURATION_MILLIS / 2
                            )
                        )
                    } else if (isSongDetailTransition) {
                        fadeOut(animationSpec = tween(durationMillis = 140))
                    } else {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(180))
                    }
                }
            ) {
                composable(AppRoute.Home.route) {
                    HomeScreen(container, innerPadding) { route -> navController.navigate(route) }
                }
                composable(AppRoute.Dan.route) {
                    DanListScreen(
                        innerPadding = innerPadding,
                        openCategory = { categoryId -> navController.navigate(AppRoute.DanDetail.create(categoryId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.UsefulLinks.route) {
                    UsefulLinksScreen(
                        innerPadding = innerPadding,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Search.route) {
                    SearchScreen(
                        container = container,
                        innerPadding = innerPadding,
                        onBottomBarVisibilityChange = { isSearchBottomBarVisible = it },
                        isBottomBarVisible = isSearchBottomBarVisible,
                        activeSharedTransitionSongId = activeSongTransitionState?.songIdentifier,
                        sharedTransitionState = activeSongTransitionState,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this
                    ) { songIdentifier, transitionState -> openSongWithSharedTransition(songIdentifier, transitionState) }
                }
                composable(AppRoute.Settings.route) {
                    SettingsScreen(container, innerPadding) { route -> navController.navigate(route) }
                }
                composable(AppRoute.Scan.route) {
                    ScannerScreen(
                        container = container,
                        innerPadding = innerPadding,
                        openSong = openSongWithoutSharedTransition
                    )
                }
                composable(AppRoute.SongDetail.route) { backStack ->
                    val songIdentifier = backStack.arguments?.getString("songIdentifier").orEmpty()
                    val decodedSongIdentifier = Uri.decode(songIdentifier)
                    val searchTransitionState = activeSongTransitionState
                        ?.takeIf { it.songIdentifier == decodedSongIdentifier }
                    SongDetailScreen(
                        container = container,
                        innerPadding = innerPadding,
                        songIdentifier = decodedSongIdentifier,
                        onBack = { navController.popBackStack() },
                        openCommunityBoard = { navController.navigate(AppRoute.CommunityAliases.route) },
                        openCloudAuth = { navController.navigate(AppRoute.CloudAuth.route) },
                        searchTransitionState = searchTransitionState,
                        sharedTransitionScope = if (searchTransitionState != null) this@SharedTransitionLayout else null,
                        animatedVisibilityScope = if (searchTransitionState != null) this else null
                    )
                }
                composable(AppRoute.DanDetail.route) { backStack ->
                    val categoryId = backStack.arguments?.getString("categoryId").orEmpty()
                    DanDetailScreen(
                        container = container,
                        innerPadding = innerPadding,
                        categoryId = Uri.decode(categoryId),
                        openSong = openSongWithoutSharedTransition,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.B50.route) {
                    B50Screen(
                        container = container,
                        innerPadding = innerPadding,
                        activeSharedTransitionSongId = activeSongTransitionState?.songIdentifier,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        openSong = openSongWithSharedTransition,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Recommendations.route) {
                    RecommendationsScreen(
                        container = container,
                        innerPadding = innerPadding,
                        activeSharedTransitionSongId = activeSongTransitionState?.songIdentifier,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        openSong = openSongWithSharedTransition,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.PlateProgress.route) {
                    PlateProgressScreen(
                        container = container,
                        innerPadding = innerPadding,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Scores.route) {
                    ScoreListScreen(
                        container = container,
                        innerPadding = innerPadding,
                        activeSharedTransitionSongId = activeSongTransitionState?.songIdentifier,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        openSong = openSongWithSharedTransition,
                        openSearch = { navController.navigate(AppRoute.Search.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Random.route) {
                    RandomSongScreen(
                        container = container,
                        innerPadding = innerPadding,
                        activeSharedTransitionSongId = activeSongTransitionState?.songIdentifier,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        openSong = openSongWithSharedTransition,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.CommunityAliases.route) {
                    CommunityAliasVotingBoardScreen(
                        container = container,
                        innerPadding = innerPadding,
                        openSong = openSongWithoutSharedTransition,
                        openLogin = { navController.navigate(AppRoute.CloudAuth.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Profiles.route) {
                    ProfilesScreen(container, innerPadding) { navController.popBackStack() }
                }
                composable(AppRoute.StaticSync.route) {
                    StaticSyncScreen(container, innerPadding) { navController.popBackStack() }
                }
                composable(AppRoute.CloudAuth.route) {
                    CloudAuthScreen(container, innerPadding) { navController.popBackStack() }
                }
                composable(AppRoute.DataImport.route) {
                    DataImportScreen(container, innerPadding) { navController.popBackStack() }
                }
            }
        }
    }
}
