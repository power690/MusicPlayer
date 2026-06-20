package com.example.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.player.data.model.MusicSong
import com.example.player.data.model.RepeatMode
import com.example.player.data.model.ShuffleMode
import com.example.player.data.repository.MusicRepository
import com.example.player.ui.screens.PlayMode
import com.example.player.player.MusicPlayerManager
import com.example.player.recommendation.RecommendationEngine
import com.example.player.service.MusicPlaybackService
import com.example.player.ui.util.BlurHelper
import com.example.player.ui.util.LyricsParser
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val SERVICE_BIND_RETRY_INTERVAL_MS = 300L
        private const val SERVICE_BIND_MAX_RETRIES = 20
    }

    private val repository = MusicRepository(application)
    val recommendationEngine = RecommendationEngine(application, repository)

    private var serviceBound = false
    private var playerManager: MusicPlayerManager? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlaybackService.MusicBinder

            if (!binder.isReady()) {
                Log.d(TAG, "Service connected but not yet initialized — scheduling retry")
                scheduleServiceBindRetry()
                return
            }
            playerManager = binder.getPlayerManager()
            serviceBound = true
            Log.d(TAG, "Service connected and initialized, starting state forwarding + progress polling")
            forwardStateFlows(playerManager!!)
            startProgressPolling(playerManager!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerManager = null
            serviceBound = false
            stopProgressPolling()
        }
    }

    private var bindRetryCount = 0
    private val bindRetryHandler = Handler(Looper.getMainLooper())
    private var bindRetryRunnable: Runnable? = null

    var hasAudioPermission by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var homeContentEverShown by mutableStateOf(false)
        private set

    fun markHomeContentShown() {
        homeContentEverShown = true
    }

    var songCount by mutableStateOf(0)
        private set

    val allSongs: StateFlow<List<MusicSong>> = repository.allSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val favoriteSongs: StateFlow<List<MusicSong>> = repository.favoriteSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val songCountFlow: StateFlow<Int> = repository.songCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val recentlyAddedSongs: StateFlow<List<MusicSong>> =
        repository.getRecentlyAddedSongsFlow(10).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    val homeBlurCache: SnapshotStateMap<String, ImageBitmap?> = mutableStateMapOf()

    private val _artistGroups = MutableStateFlow<List<ArtistGroupData>>(emptyList())
    val artistGroups: StateFlow<List<ArtistGroupData>> = _artistGroups.asStateFlow()

    private val _albumGroups = MutableStateFlow<List<AlbumGroupData>>(emptyList())
    val albumGroups: StateFlow<List<AlbumGroupData>> = _albumGroups.asStateFlow()

    init {
        bindToService()
        observeData()

        viewModelScope.launch {
            allSongs.collect { songs ->
                computeArtistAndAlbumGroups(songs)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {

                kotlinx.coroutines.delay(1500)
                _suggestions.value = recommendationEngine.getSuggestions(8)

                kotlinx.coroutines.delay(500)
                val moreSuggestions = recommendationEngine.getSuggestions(20)
                _suggestions.value = moreSuggestions
                _featured.value = recommendationEngine.getFeaturedSongs(10)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recommendations: ${e.message}")
                try {
                    _suggestions.value = repository.getRandomSongs(8)
                    _featured.value = repository.getRandomSongs(10)
                } catch (_: Exception) {}
            }
        }
    }

    private fun computeArtistAndAlbumGroups(songs: List<MusicSong>) {

        val artistFrequency = mutableMapOf<String, Int>()
        songs.filter { it.artist.isNotEmpty() }.forEach { song ->
            song.artist
                .split("&", "/", "、", "feat.", "Feat.", "FEAT.", "×", "x")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { name ->
                    artistFrequency[name] = (artistFrequency[name] ?: 0) + 1
                }
        }
        val artists = songs.filter { it.artist.isNotEmpty() }
            .groupBy { song ->
                val parts = song.artist
                    .split("&", "/", "、", "feat.", "Feat.", "FEAT.", "×", "x")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                parts.maxByOrNull { artistFrequency[it] ?: 0 } ?: song.artist
            }
            .map { (artist, songList) ->
                ArtistGroupData(
                    name = artist,
                    songCount = songList.size,
                    coverArtUri = songList.firstOrNull()?.albumArtUri
                )
            }
            .sortedBy { it.name }
        _artistGroups.value = artists

        val albums = songs
            .filter { it.albumId != 0L }
            .groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                AlbumGroupData(
                    albumId = albumId,
                    albumName = albumSongs.firstOrNull()?.album ?: "未知专辑",
                    artist = albumSongs.firstOrNull()?.artist ?: "",
                    coverArtUri = albumSongs.firstOrNull()?.albumArtUri
                )
            }
            .sortedBy { it.albumName }
        _albumGroups.value = albums
    }

    private val _showPlayerOverlay = MutableStateFlow(false)
    val showPlayerOverlay: StateFlow<Boolean> = _showPlayerOverlay.asStateFlow()

    fun showPlayer() { _showPlayerOverlay.value = true }
    fun hidePlayer() { _showPlayerOverlay.value = false }

    private val _playerBlurredBackground = MutableStateFlow<ImageBitmap?>(null)
    val playerBlurredBackground: StateFlow<ImageBitmap?> = _playerBlurredBackground.asStateFlow()

    private val _playerLyrics = MutableStateFlow<List<LyricsParser.LyricLine>?>(null)
    val playerLyrics: StateFlow<List<LyricsParser.LyricLine>?> = _playerLyrics.asStateFlow()

    private val _currentSong = MutableStateFlow<MusicSong?>(null)
    val currentSong: StateFlow<MusicSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(ShuffleMode.OFF)
    val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode.asStateFlow()

    private val _playQueue = MutableStateFlow<List<MusicSong>>(emptyList())
    val playQueue: StateFlow<List<MusicSong>> = _playQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _suggestions = MutableStateFlow<List<MusicSong>>(emptyList())
    val suggestions: StateFlow<List<MusicSong>> = _suggestions.asStateFlow()

    private val _featured = MutableStateFlow<List<MusicSong>>(emptyList())
    val featured: StateFlow<List<MusicSong>> = _featured.asStateFlow()

    private fun scheduleServiceBindRetry() {
        if (bindRetryCount >= SERVICE_BIND_MAX_RETRIES || serviceBound) return
        bindRetryRunnable = Runnable {
            if (!serviceBound) {
                bindRetryCount++
                Log.d(TAG, "Retrying service bind (attempt $bindRetryCount/$SERVICE_BIND_MAX_RETRIES)")
                try {
                    val intent = Intent(getApplication<Application>(), MusicPlaybackService::class.java)
                    getApplication<Application>().unbindService(serviceConnection)
                    getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    Log.e(TAG, "Service bind retry failed: ${e.message}")
                }
            }
        }
        bindRetryHandler.postDelayed(bindRetryRunnable!!, SERVICE_BIND_RETRY_INTERVAL_MS)
    }

    private fun forwardStateFlows(pm: MusicPlayerManager) {
        viewModelScope.launch {
            pm.currentSong.collect { song ->
                _currentSong.value = song

                preloadPlayerData(song)
            }
        }
        viewModelScope.launch {
            pm.isPlaying.collect { _isPlaying.value = it }
        }
        viewModelScope.launch {
            pm.repeatMode.collect { _repeatMode.value = it }
        }
        viewModelScope.launch {
            pm.shuffleMode.collect { _shuffleMode.value = it }
        }
        viewModelScope.launch {
            pm.playQueue.collect { _playQueue.value = it }
        }
        viewModelScope.launch {
            pm.currentIndex.collect { _currentIndex.value = it }
        }
        viewModelScope.launch {
            pm.speed.collect { _speed.value = it }
        }
    }

    private fun startProgressPolling(pm: MusicPlayerManager) {
        stopProgressPolling()
        val player = pm.exoPlayer
        var logCount = 0
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    val buf = player.bufferedPosition
                    _currentPosition.value = pos
                    _duration.value = dur
                    _bufferedPosition.value = buf
                    if (logCount++ < 3) {
                        Log.d(TAG, "Progress polling #$logCount: pos=$pos dur=$dur isPlaying=${player.isPlaying}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Progress polling error: ${e.message}")

                    try { player.playWhenReady } catch (_: Exception) { return }
                }
                progressRunnable = this
                progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
        progressRunnable = runnable
        progressHandler.post(runnable)
        Log.d(TAG, "Progress polling started via Handler on main thread")
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun bindToService() {
        val intent = Intent(getApplication<Application>(), MusicPlaybackService::class.java)
        MusicPlaybackService.startService(getApplication())
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeData() {
        viewModelScope.launch {
            songCountFlow.collect { count ->
                songCount = count
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _suggestions.value = recommendationEngine.getSuggestions(20)
                _featured.value = recommendationEngine.getFeaturedSongs(20)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recommendations: ${e.message}")
            }
        }
    }

    fun onPermissionGranted() {
        hasAudioPermission = true
        scanMusic()
    }

    fun scanMusic() {
        if (isScanning) return
        isScanning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.scanLocalMusic()
                recommendationEngine.forceRefresh()
                _suggestions.value = recommendationEngine.getSuggestions(20)
                _featured.value = recommendationEngine.getFeaturedSongs(20)
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning music: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isScanning = false
                }
            }
        }
    }

    fun playSong(song: MusicSong) {
        viewModelScope.launch {
            val songs = repository.getAllSongsOnce()
            val index = songs.indexOfFirst { it.id == song.id }
            if (index >= 0) {
                playerManager?.setQueue(songs, index)
            } else {
                playerManager?.playSong(song)
            }
        }
    }

    fun playAll(songs: List<MusicSong>, startIndex: Int = 0) {
        playerManager?.setQueue(songs, startIndex)
    }

    fun playSongFromList(songs: List<MusicSong>, index: Int) {
        playerManager?.setQueue(songs, index)
    }

    fun togglePlayPause() = playerManager?.togglePlayPause()
    fun next() = playerManager?.next()
    fun previous() = playerManager?.previous()
    fun seekTo(position: Long) = playerManager?.seekTo(position)
    fun seekToPercent(percent: Float) = playerManager?.seekToPercent(percent)

    fun cycleRepeatMode() = playerManager?.cycleRepeatMode()
    fun toggleShuffleMode() = playerManager?.toggleShuffleMode()

    fun applyPlayMode(mode: PlayMode) {
        when (mode) {
            PlayMode.SEQUENTIAL -> {
                playerManager?.setRepeatMode(RepeatMode.OFF)
                playerManager?.setShuffleMode(ShuffleMode.OFF)
            }
            PlayMode.SHUFFLE -> {
                playerManager?.setRepeatMode(RepeatMode.OFF)
                playerManager?.setShuffleMode(ShuffleMode.ON)
            }
            PlayMode.SINGLE_LOOP -> {
                playerManager?.setRepeatMode(RepeatMode.ONE)
                playerManager?.setShuffleMode(ShuffleMode.OFF)
            }
        }
    }

    fun playFromQueue(index: Int) = playerManager?.playSongAtIndex(index)
    fun removeFromQueue(index: Int) = playerManager?.removeFromQueue(index)
    fun clearQueue() = playerManager?.clearQueue()

    fun setSpeed(speed: Float) = playerManager?.setSpeed(speed)

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(songId)
        }
    }

    fun toggleFavoriteAndUpdateUI(song: MusicSong) {
        val newFavorite = !song.isFavorite

        _currentSong.value = song.copy(isFavorite = newFavorite)
        playerManager?.updateSongFavorite(song.id, newFavorite)

        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(song.id)
        }
    }

    fun searchSongs(query: String): Flow<List<MusicSong>> = repository.searchSongs(query)

    fun getSongsByAlbum(albumId: Long): Flow<List<MusicSong>> = repository.getSongsByAlbum(albumId)
    fun getSongsByArtist(artist: String): Flow<List<MusicSong>> = repository.getSongsByArtist(artist)
    fun getSongsByGenre(genre: String): Flow<List<MusicSong>> = repository.getSongsByGenre(genre)
    fun getAllArtists(): Flow<List<String>> = repository.allArtists
    fun getAllAlbums(): Flow<List<String>> = repository.allAlbums
    fun getAllGenres(): Flow<List<String>> = repository.allGenres

    val allPlaylists = repository.allPlaylists
    fun createPlaylist(name: String) = viewModelScope.launch { repository.createPlaylist(name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun getSongsInPlaylist(id: Long) = repository.getSongsInPlaylist(id)

    private fun preloadPlayerData(song: MusicSong?) {

        viewModelScope.launch(Dispatchers.IO) {
            val uri = song?.albumArtUri
            if (uri != null) {
                val blurred = BlurHelper.loadBlurredBitmap(getApplication(), uri, targetSize = 400, blurPixels = 32)
                _playerBlurredBackground.value = blurred?.asImageBitmap()
            } else {
                _playerBlurredBackground.value = null
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (song != null) {
                val lyrics = LyricsParser.loadLyricsForSong(song.embeddedLyrics, song.path)
                _playerLyrics.value = lyrics
            } else {
                _playerLyrics.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressPolling()
        bindRetryRunnable?.let { bindRetryHandler.removeCallbacks(it) }
        bindRetryRunnable = null
        if (serviceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service: ${e.message}")
            }
            serviceBound = false
        }
    }
}

data class ArtistGroupData(
    val name: String,
    val songCount: Int,
    val coverArtUri: String?
)

data class AlbumGroupData(
    val albumId: Long,
    val albumName: String,
    val artist: String,
    val coverArtUri: String?
)
