package com.example.player.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.player.data.model.MusicSong
import com.example.player.data.model.RepeatMode
import com.example.player.data.model.ShuffleMode
import com.example.player.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random

class MusicPlayerManager(
    private val context: Context,
    private val repository: MusicRepository
) : Player.Listener {

    companion object {
        private const val TAG = "MusicPlayerManager"
        private const val SHUFFLE_HISTORY_SIZE = 20
        private const val MEDIA_SESSION_UPDATE_INTERVAL_MS = 500L
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            false
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "MusicSession").apply {
        isActive = true
    }

    private val _currentSong = MutableStateFlow<MusicSong?>(null)
    val currentSong: StateFlow<MusicSong?> = _currentSong.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackStateCompat.STATE_NONE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

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

    private val shuffleHistory = mutableListOf<Int>()
    private val random = Random()

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var pausedByAudioFocusLoss = false

    @Volatile
    private var hasAudioFocus = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {

                Log.d(TAG, "Audio focus: GAIN — checking auto-resume")
                if (pausedByAudioFocusLoss) {
                    pausedByAudioFocusLoss = false
                    Log.d(TAG, "Audio focus: GAIN — auto resuming playback")
                    exoPlayer.volume = 1.0f
                    exoPlayer.playWhenReady = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {

                Log.d(TAG, "Audio focus: LOSS — pausing, waiting for user to resume")
                pausedByAudioFocusLoss = false
                hasAudioFocus = false
                exoPlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {

                Log.d(TAG, "Audio focus: LOSS_TRANSIENT — pausing")
                pausedByAudioFocusLoss = true
                exoPlayer.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {

                Log.d(TAG, "Audio focus: LOSS_TRANSIENT_CAN_DUCK — pausing")
                pausedByAudioFocusLoss = true
                exoPlayer.playWhenReady = false
            }
        }
    }

    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .build()
        } else {
            null
        }
    }

    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        hasAudioFocus = result
        Log.d(TAG, "requestAudioFocus: ${if (result) "granted" else "denied"}")
        return result
    }

    fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        pausedByAudioFocusLoss = false
        Log.d(TAG, "abandonAudioFocus: released")
    }

    @Volatile
    private var _isManualSkip = false

    private val mediaSessionHandler = Handler(Looper.getMainLooper())
    private var mediaSessionRunnable: Runnable? = null

    init {
        exoPlayer.addListener(this)
        startMediaSessionProgressUpdates()
    }

    fun setQueue(songs: List<MusicSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) return

        _playQueue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, songs.size - 1)

        if (_shuffleMode.value == ShuffleMode.ON) {
            shuffleHistory.clear()
            shuffleHistory.add(startIndex)
        }

        playSongAtIndex(startIndex)
    }

    fun addToQueue(song: MusicSong) {
        val currentQueue = _playQueue.value.toMutableList()
        currentQueue.add(song)
        _playQueue.value = currentQueue
    }

    fun addToNext(song: MusicSong) {
        val currentQueue = _playQueue.value.toMutableList()
        val insertIndex = _currentIndex.value + 1
        currentQueue.add(insertIndex.coerceAtMost(currentQueue.size), song)
        _playQueue.value = currentQueue
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= _playQueue.value.size) return
        val currentQueue = _playQueue.value.toMutableList()
        currentQueue.removeAt(index)
        _playQueue.value = currentQueue

        if (currentQueue.isEmpty()) {
            stop()
            return
        }

        if (index < _currentIndex.value) {
            _currentIndex.value = _currentIndex.value - 1
        } else if (index == _currentIndex.value) {
            if (_currentIndex.value >= currentQueue.size) {
                _currentIndex.value = currentQueue.size - 1
            }
            playSongAtIndex(_currentIndex.value)
        }
    }

    fun clearQueue() {
        _playQueue.value = emptyList()
        _currentIndex.value = 0
        _currentSong.value = null
        stop()
    }

    fun updateSongFavorite(songId: Long, isFavorite: Boolean) {
        val queue = _playQueue.value.toMutableList()
        val index = queue.indexOfFirst { it.id == songId }
        if (index >= 0) {
            queue[index] = queue[index].copy(isFavorite = isFavorite)
            _playQueue.value = queue
        }

        if (_currentSong.value?.id == songId) {
            _currentSong.value = _currentSong.value!!.copy(isFavorite = isFavorite)
        }
    }

    fun playSong(song: MusicSong) {
        val queue = _playQueue.value.toMutableList()
        val index = queue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playSongAtIndex(index)
        } else {
            queue.add(song)
            _playQueue.value = queue
            _currentIndex.value = queue.size - 1
            playSongAtIndex(queue.size - 1)
        }
    }

    fun playSongAtIndex(index: Int) {
        val queue = _playQueue.value
        if (index < 0 || index >= queue.size) {
            Log.w(TAG, "playSongAtIndex: invalid index=$index, queueSize=${queue.size}")
            return
        }

        _currentIndex.value = index
        val song = queue[index]
        _currentSong.value = song

        if (song.path.isNotEmpty() && !java.io.File(song.path).exists()) {
            Log.w(TAG, "Ghost file detected, skipping: ${song.title} (path=${song.path})")

            playerScope.launch {
                try { repository.deleteSongById(song.id) } catch (_: Exception) {}
            }

            if (queue.size > 1) {
                next()
            } else {

                stop()
            }
            return
        }

        requestAudioFocus()

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(song.path))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song at index $index: ${e.message}")
        }

        try {
            playerScope.launch {
                try { repository.recordPlay(song.id) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        Log.d(TAG, "Playing: ${song.title} - ${song.artist} [index=$index, queueSize=${queue.size}]")
    }

    fun play() {
        if (_playQueue.value.isEmpty()) return

        requestAudioFocus()

        if (_currentSong.value == null) {
            playSongAtIndex(_currentIndex.value)
        } else {
            exoPlayer.playWhenReady = true
        }
    }

    fun pause() {

        pausedByAudioFocusLoss = false
        exoPlayer.playWhenReady = false
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
        exoPlayer.stop()
        _currentSong.value = null
        _playbackState.value = PlaybackStateCompat.STATE_STOPPED
        pausedByAudioFocusLoss = false

        abandonAudioFocus()
    }

    fun next() {
        val queue = _playQueue.value
        if (queue.isEmpty()) return

        _isManualSkip = true

        playerScope.launch {
            try { _currentSong.value?.let { repository.recordSkip(it.id) } } catch (_: Exception) {}
        }

        try {
            if (_shuffleMode.value == ShuffleMode.ON) {
                playShuffledNext()
            } else {

                val nextIndex = (_currentIndex.value + 1) % queue.size
                playSongAtIndex(nextIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in next(): ${e.message}")
            _isManualSkip = false
            return
        }

        mediaSessionHandler.postDelayed({ _isManualSkip = false }, 800)
    }

    fun previous() {
        val queue = _playQueue.value
        if (queue.isEmpty()) return

        _isManualSkip = true

        try {
            if (_shuffleMode.value == ShuffleMode.ON) {
                if (shuffleHistory.size > 1) {
                    shuffleHistory.removeLast()
                    val targetIndex = shuffleHistory.last()
                    Log.d(TAG, "Previous (shuffle): history size=${shuffleHistory.size}, target=$targetIndex")
                    exoPlayer.stop()
                    playSongAtIndex(targetIndex)
                } else {

                    Log.d(TAG, "Previous (shuffle): at history start, restarting current")
                    exoPlayer.seekTo(0)
                    if (!exoPlayer.isPlaying) {
                        exoPlayer.playWhenReady = true
                    }
                    _isManualSkip = false
                    return
                }
            } else {

                val prevIndex = (_currentIndex.value - 1 + queue.size) % queue.size
                Log.d(TAG, "Previous: currentIndex=${_currentIndex.value} -> prevIndex=$prevIndex")

                exoPlayer.stop()
                playSongAtIndex(prevIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in previous(): ${e.message}")
            _isManualSkip = false
            return
        }

        mediaSessionHandler.postDelayed({ _isManualSkip = false }, 800)
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    fun seekToPercent(percent: Float) {
        val durationMs = exoPlayer.duration.coerceAtLeast(0L)
        exoPlayer.seekTo((durationMs * percent).toLong())
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        when (mode) {
            RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    fun cycleRepeatMode() {
        setRepeatMode(_repeatMode.value.next())
    }

    fun setShuffleMode(mode: ShuffleMode) {
        _shuffleMode.value = mode
        if (mode == ShuffleMode.ON) {
            shuffleHistory.clear()
            shuffleHistory.add(_currentIndex.value)
        }
    }

    fun toggleShuffleMode() {
        setShuffleMode(_shuffleMode.value.toggle())
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed.coerceIn(0.25f, 3.0f)
        exoPlayer.playbackParameters = PlaybackParameters(_speed.value)
    }

    private fun startMediaSessionProgressUpdates() {

        Log.d(TAG, "Media session periodic updates disabled — handled by MusicNotificationManager")
    }

    private fun stopMediaSessionProgressUpdates() {
        mediaSessionRunnable?.let { mediaSessionHandler.removeCallbacks(it) }
        mediaSessionRunnable = null
    }

    override fun onPlaybackStateChanged(state: Int) {
        _playbackState.value = when (state) {
            Player.STATE_IDLE -> PlaybackStateCompat.STATE_NONE
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY -> {
                if (exoPlayer.playWhenReady) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED
            }
            Player.STATE_ENDED -> {

                onSongEnded()
                PlaybackStateCompat.STATE_STOPPED
            }
            else -> PlaybackStateCompat.STATE_NONE
        }

        if (state == Player.STATE_READY) {
            _duration.value = exoPlayer.duration
        }

    }

    private fun onSongEnded() {

        if (_isManualSkip) {
            Log.d(TAG, "onSongEnded: suppressed — manual skip in progress")
            return
        }

        val queue = _playQueue.value
        if (queue.isEmpty()) return

        Log.d(TAG, "Song ended, repeatMode=${_repeatMode.value}, shuffleMode=${_shuffleMode.value}")

        try {
            if (_repeatMode.value == RepeatMode.ONE) {

                exoPlayer.seekTo(0)
                exoPlayer.playWhenReady = true
            } else if (_shuffleMode.value == ShuffleMode.ON) {
                playShuffledNext()
            } else {

                next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSongEnded: ${e.message}")
        }
    }

    override fun onIsPlayingChanged(playing: Boolean) {
        _isPlaying.value = playing

    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        _speed.value = playbackParameters.speed
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Player error: ${error.message}", error)

        try {
            if (_playQueue.value.size > 1) {
                next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during skip after player error: ${e.message}")
        }
    }

    private fun playShuffledNext() {
        val queue = _playQueue.value
        if (queue.size <= 1) return

        val recentSet = shuffleHistory.takeLast(SHUFFLE_HISTORY_SIZE / 2).toSet()
        val candidates = (0 until queue.size).filter { it !in recentSet }

        val nextIndex = if (candidates.isNotEmpty()) {
            candidates[random.nextInt(candidates.size)]
        } else {
            random.nextInt(queue.size)
        }

        shuffleHistory.add(nextIndex)
        if (shuffleHistory.size > SHUFFLE_HISTORY_SIZE) {
            shuffleHistory.removeFirst()
        }

        playSongAtIndex(nextIndex)
    }

    private fun updateMediaSessionPlaybackState() {

    }

    fun release() {
        stopMediaSessionProgressUpdates()
        abandonAudioFocus()
        playerScope.coroutineContext[Job]?.cancel()
        exoPlayer.release()
        mediaSession.release()
    }
}
