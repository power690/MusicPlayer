package com.example.player.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.player.MainViewModel
import com.example.player.ui.screens.*

@Composable
fun MusicNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    isOnMainTab: Boolean = true
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,

        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = EaseOut)
            )
        },

        exitTransition = { ExitTransition.None },

        popEnterTransition = { EnterTransition.None },

        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(250, easing = EaseIn)
            )
        },
    ) {

        composable(Screen.Main.route) {

            Box(modifier = Modifier.fillMaxSize())
        }

        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = viewModel,
                onSongClick = { song ->
                    viewModel.showPlayer()
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("favorites_detail") {
            FavoritesScreen(
                viewModel = viewModel,
                onSongClick = { song ->
                    viewModel.showPlayer()
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            AlbumDetailScreen(
                albumId = albumId,
                viewModel = viewModel,
                onSongClick = { _ ->
                    viewModel.showPlayer()
                },
                onBackClick = { navController.popBackStack() },
                onPlayAll = {
                    viewModel.showPlayer()
                }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artist") { type = NavType.StringType })
        ) { backStackEntry ->
            val artist = backStackEntry.arguments?.getString("artist") ?: ""
            ArtistDetailScreen(
                artist = artist,
                viewModel = viewModel,
                onSongClick = { _ ->
                    viewModel.showPlayer()
                },
                onBackClick = { navController.popBackStack() },
                onPlayAll = {
                    viewModel.showPlayer()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            PlaylistDetailScreen(
                playlistId = playlistId,
                viewModel = viewModel,
                onSongClick = { _ ->
                    viewModel.showPlayer()
                },
                onBackClick = { navController.popBackStack() },
                onPlayAll = {
                    viewModel.showPlayer()
                }
            )
        }

        composable(
            route = Screen.SongList.route,
            arguments = listOf(navArgument("title") { type = NavType.StringType })
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val songs = when (title) {
                "猜你喜欢" -> viewModel.suggestions.value
                "精选歌曲" -> viewModel.featured.value
                "最近添加" -> viewModel.allSongs.value.sortedByDescending { it.dateAdded }
                else -> emptyList()
            }
            SongListScreen(
                title = title,
                songs = songs,
                onSongClick = { _ ->
                    viewModel.showPlayer()
                },
                onPlayAll = { songList ->
                    if (songList.isNotEmpty()) {
                        viewModel.playAll(songList)
                        viewModel.showPlayer()
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
