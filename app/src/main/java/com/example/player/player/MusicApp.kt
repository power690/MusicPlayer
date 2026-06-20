package com.example.player

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.player.ui.navigation.MusicNavGraph
import com.example.player.ui.navigation.Screen
import com.example.player.ui.screens.HomeScreen
import com.example.player.ui.screens.LibraryScreen
import com.example.player.ui.screens.NowPlayingCard
import com.example.player.ui.screens.PlayerScreen
import com.example.player.ui.screens.ProfileScreen
import com.example.player.ui.theme.DarkBackground
import com.example.player.ui.theme.DarkBottomBarBackground
import com.example.player.ui.theme.LightBackground
import com.example.player.ui.theme.BottomBarBackground
import com.example.player.ui.theme.getThemeColorOption

import androidx.compose.foundation.background
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DarkModeManager {
    private val _darkModeEnabled = MutableStateFlow<Boolean?>(null)
    val darkModeEnabled: StateFlow<Boolean?> = _darkModeEnabled.asStateFlow()

    fun setDarkMode(enabled: Boolean?) {
        _darkModeEnabled.value = enabled
    }
}

object ThemeColorManager {
    private val _themeColorIndex = MutableStateFlow(2)
    val themeColorIndex: StateFlow<Int> = _themeColorIndex.asStateFlow()

    fun setThemeColorIndex(index: Int) {
        _themeColorIndex.value = index
    }
}

private data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

private val BOTTOM_ITEMS = listOf(
    BottomNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("音乐库", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomNavItem("我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun MusicApp(
    navController: NavHostController,
    viewModel: MainViewModel,
    requestAudioPermission: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val hazeState = remember { HazeState() }

    val supportsBlur = Build.VERSION.SDK_INT >= 31

    val darkModePref by DarkModeManager.darkModeEnabled.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = darkModePref ?: systemDark

    val themeColorIndex by ThemeColorManager.themeColorIndex.collectAsState()
    val themeColor = getThemeColorOption(themeColorIndex)

    val showPlayerOverlay by viewModel.showPlayerOverlay.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val detailRoutes = remember {
        setOf(
            Screen.AlbumDetail.route,
            Screen.ArtistDetail.route,
            Screen.PlaylistDetail.route,
            Screen.Settings.route,
            Screen.SongList.route,
            Screen.Search.route,
            "favorites_detail"
        )
    }
    val isOnDetailPage = currentRoute in detailRoutes
    val showBottomBar = !isOnDetailPage

    val tabSlideFraction by animateFloatAsState(
        targetValue = if (isOnDetailPage) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isOnDetailPage) 300 else 250,
            easing = if (isOnDetailPage) EaseOut else EaseIn
        ),
        label = "tabSlide"
    )

    val view = LocalView.current

    val activity = (view.context as? Activity)
    val lightStatusBar = if (showPlayerOverlay) {
        false
    } else {
        !useDarkTheme
    }

    LaunchedEffect(showPlayerOverlay, useDarkTheme, themeColorIndex) {
        try {
            val act = activity
            if (act != null && !act.isDestroyed && !act.isFinishing) {
                act.runOnUiThread {
                    try {
                        val window = act.window

                        if (showPlayerOverlay) {
                            window.statusBarColor = android.graphics.Color.TRANSPARENT
                            window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        } else {
                            val bgColor = if (useDarkTheme) DarkBackground else LightBackground
                            val barColor = if (useDarkTheme) DarkBottomBarBackground else BottomBarBackground
                            val tintedBgColor = themeColor.primary.copy(alpha = 0.05f).compositeOver(bgColor)
                            window.statusBarColor = tintedBgColor.toArgb()
                            window.navigationBarColor = barColor.toArgb()
                        }

                        if (Build.VERSION.SDK_INT >= 34) {
                            window.isStatusBarContrastEnforced = false
                            window.isNavigationBarContrastEnforced = false
                        }
                    } catch (_: Exception) {}
                }

                val animDuration = if (showPlayerOverlay) 350L else 300L
                kotlinx.coroutines.delay(animDuration)

                act.runOnUiThread {
                    try {
                        val decorView = act.window.decorView
                        var uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (lightStatusBar) {
                                uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            } else {
                                uiOptions = uiOptions and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            }
                        }
                        decorView.systemUiVisibility = uiOptions
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    val navBarBaseColor = if (useDarkTheme) DarkBackground else LightBackground
    val tintedNavBarColor = themeColor.primary.copy(alpha = 0.05f).compositeOver(navBarBaseColor)

    val blurStyle = if (useDarkTheme) {
        HazeStyle(
            tint = tintedNavBarColor.copy(alpha = 0.5f),
            blurRadius = 28.dp,
            noiseFactor = 0.02f,
        )
    } else {
        HazeStyle(
            tint = tintedNavBarColor.copy(alpha = 0.5f),
            blurRadius = 28.dp,
            noiseFactor = 0.02f,
        )
    }

    val fallbackBottomBarColor = tintedNavBarColor

    Scaffold(
        containerColor = if (showPlayerOverlay) Color.Transparent else themeColor.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.background),
        bottomBar = {
            if (showBottomBar && !showPlayerOverlay) {

                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = -size.width * tabSlideFraction
                        }
                        .then(
                            if (supportsBlur) {
                                Modifier.hazeChild(state = hazeState, style = blurStyle)
                            } else {
                                Modifier.background(fallbackBottomBarColor)
                            }
                        )
                ) {

                    NowPlayingCardWrapper(
                        viewModel = viewModel,
                        onClick = { viewModel.showPlayer() }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BOTTOM_ITEMS.forEachIndexed { index, item ->
                            val selected = selectedTab == index && !isOnDetailPage
                            NavigationBarItem(
                                icon = {
                                    Box(
                                        modifier = Modifier.size(38.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                            tint = if (selected) themeColor.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        color = if (selected) themeColor.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                },
                                selected = selected,
                                onClick = {

                                    selectedTab = index

                                    if (isOnDetailPage) {
                                        navController.popBackStack(Screen.Main.route, inclusive = false)
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (supportsBlur) Modifier.haze(hazeState) else Modifier
                )
                .clipToBounds()
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -size.width * tabSlideFraction
                    }
            ) {

                val animatedTab by animateFloatAsState(
                    targetValue = selectedTab.toFloat(),
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "tabSlide"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (0f - animatedTab) * size.width

                            alpha = if (translationX > -size.width && translationX < size.width) 1f else 0f
                        }
                ) {
                    HomeScreen(
                        viewModel = viewModel,
                        onSongClick = { song ->
                            viewModel.showPlayer()
                        },
                        onSeeAllClick = { title ->
                            navController.navigate(Screen.SongList.createRoute(title))
                        },
                        onNavigateToFavorites = {
                            selectedTab = 2
                        },
                        onNavigateToLibrary = {
                            selectedTab = 1
                        },
                        onNavigateToSearch = {
                            navController.navigate(Screen.Search.route) {
                                launchSingleTop = true
                            }
                        },
                        onRequestPermission = requestAudioPermission
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - animatedTab) * size.width
                            alpha = if (translationX > -size.width && translationX < size.width) 1f else 0f
                        }
                ) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onSongClick = { song ->
                            viewModel.showPlayer()
                        },
                        onAlbumClick = { albumId ->
                            navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                        },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist))
                        },
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (2f - animatedTab) * size.width
                            alpha = if (translationX > -size.width && translationX < size.width) 1f else 0f
                        }
                ) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onNavigateToFavorites = {
                            navController.navigate("favorites_detail") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToLibrary = {
                            selectedTab = 1
                        }
                    )
                }
            }

            MusicNavGraph(
                navController = navController,
                viewModel = viewModel,
                isOnMainTab = !isOnDetailPage
            )

            val context = LocalContext.current
            var lastBackTime by remember { mutableLongStateOf(0L) }

            BackHandler(enabled = showPlayerOverlay) {
                viewModel.hidePlayer()
            }

            BackHandler(enabled = !showPlayerOverlay && isOnDetailPage) {
                navController.popBackStack(Screen.Main.route, inclusive = false)
            }

            BackHandler(enabled = !showPlayerOverlay && !isOnDetailPage) {
                val now = System.currentTimeMillis()
                if (now - lastBackTime < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackTime = now
                    Toast.makeText(context, "再按一次退出程序", Toast.LENGTH_SHORT).show()
                }
            }

            AnimatedVisibility(
                visible = showPlayerOverlay,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                PlayerScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.hidePlayer() }
                )
            }
        }
    }
}

fun applyStatusBarIcons(window: android.view.Window, lightStatusBar: Boolean) {
    try {
        val decorView = window.decorView
        var uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (lightStatusBar) {
                uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                uiOptions = uiOptions and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        decorView.systemUiVisibility = uiOptions

        if (Build.VERSION.SDK_INT >= 34) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    } catch (_: Exception) {}
}

@Composable
private fun NowPlayingCardWrapper(
    viewModel: MainViewModel,
    onClick: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    currentSong?.let { song ->
        NowPlayingCard(
            song = song,
            isPlaying = isPlaying,
            viewModel = viewModel,
            onClick = onClick
        )
    }
}
