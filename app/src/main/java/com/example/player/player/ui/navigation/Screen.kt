package com.example.player.ui.navigation

sealed class Screen(val route: String) {

    data object Main : Screen("main")

    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Favorites : Screen("favorites")
    data object Player : Screen("player")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artist}") {
        fun createRoute(artist: String) = "artist/$artist"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
    data object Settings : Screen("settings")
    data object SongList : Screen("songlist/{title}") {
        fun createRoute(title: String) = "songlist/$title"
    }
}
