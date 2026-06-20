package com.example.player.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.player.data.model.MusicSong
import com.example.player.data.model.RepeatMode
import com.example.player.data.model.ShuffleMode
import com.example.player.data.repository.MusicRepository
import com.example.player.notification.MusicNotificationManager
import com.example.player.player.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicPlaybackService : Service() {

    companion object {
        private const val TAG = "MusicPlaybackService"

        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

        private const val NOTIFICATION_REBUILD_INTERVAL_MS = 1000L

        const val ACTION_PLAY = "com.example.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"
        const val ACTION_PREV = "com.example.player.ACTION_PREV"
        const val ACTION_CLOSE = "com.example.player.ACTION_CLOSE"
        const val ACTION_FAVORITE = "com.example.player.ACTION_FAVORITE"
        const val ACTION_PLAY_MODE = "com.example.player.ACTION_PLAY_MODE"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private fun startServiceForeground(service: Service, id: Int, notification: Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                service.startForeground(id, notification)
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            context.stopService(intent)
        }
    }

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) { handleAppForeground() }
        override fun onPause(owner: LifecycleOwner) { handleAppBackground() }
    }

    private fun handleAppForeground() {
        if (_isAppInForeground.value) return
        _isAppInForeground.value = true
        Log.d(TAG, "App → foreground — detaching notification")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION") stopForeground(false)
            }
        } catch (e: Exception) { Log.w(TAG, "stopForeground detach failed: ${e.message}") }
        if (::notificationManager.isInitialized) {
            notificationManager.cancelNotification()
        }
    }

    private fun handleAppBackground() {
        if (!_isAppInForeground.value) return
        _isAppInForeground.value = false
        if (!::playerManager.isInitialized) return
        val song = playerManager.currentSong.value
        if (song != null) {
            Log.d(TAG, "App → background — showing notification")
            postNotificationUpdate()

            Handler(Looper.getMainLooper()).postDelayed({
                if (!_isAppInForeground.value && playerManager.currentSong.value != null) {
                    postNotificationUpdate()
                }
            }, 200)
        } else {
            Log.d(TAG, "App → background — no song, removing notification")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION") stopForeground(true)
                }
            } catch (e: Exception) { Log.w(TAG, "stopForeground failed: ${e.message}") }
            if (::notificationManager.isInitialized) {
                notificationManager.cancelNotification()
            }
        }
    }

    private val binder = MusicBinder()
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var notificationManager: MusicNotificationManager
    private lateinit var repository: MusicRepository
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Volatile
    private var isInitialized = false

    private var lastNotificationRebuildTime: Long = 0L

    private var lastNotificationSongId: Long = -1L

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updatePeriodicProgress()
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    inner class MusicBinder : Binder() {
        fun getPlayerManager(): MusicPlayerManager = playerManager
        fun getNotificationManager(): MusicNotificationManager = notificationManager
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
        fun isReady(): Boolean = isInitialized
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        val placeholder = buildPlaceholderNotification()
        startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, placeholder)
        Log.d(TAG, "startForeground() called immediately")

        val appLifecycle = ProcessLifecycleOwner.get().lifecycle
        appLifecycle.addObserver(appLifecycleObserver)
        _isAppInForeground.value = appLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        _isServiceRunning.value = true

        Handler(Looper.getMainLooper()).post {
            performHeavyInitialization()

            if (_isAppInForeground.value) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                } catch (e: Exception) { Log.w(TAG, "stopForeground on init failed: ${e.message}") }
                notificationManager.cancelNotification()
            }

            progressHandler.post(progressRunnable)
            observePlaybackStateChanges()
            Log.d(TAG, "Service fully initialized")
        }
    }

    private fun observePlaybackStateChanges() {

        serviceScope.launch {
            var lastIsPlaying = playerManager.isPlaying.value
            playerManager.isPlaying.collect { isPlaying ->
                if (isPlaying != lastIsPlaying) {
                    lastIsPlaying = isPlaying
                    Log.d(TAG, "isPlaying changed: $isPlaying")

                    val position = playerManager.exoPlayer.currentPosition
                    val duration = playerManager.exoPlayer.duration.coerceAtLeast(0L)
                    val buffered = playerManager.exoPlayer.bufferedPosition

                    notificationManager.updateMediaSessionPlaybackState(
                        isPlaying = isPlaying,
                        currentPosition = position,
                        bufferedPosition = buffered,
                        speed = playerManager.exoPlayer.playbackParameters.speed,
                        duration = duration
                    )

                    if (!_isAppInForeground.value) {
                        postNotificationUpdate()
                    }
                }
            }
        }

        serviceScope.launch {
            var lastSongId = playerManager.currentSong.value?.id
            playerManager.currentSong.collect { song ->
                if (song?.id != lastSongId) {
                    lastSongId = song?.id
                    Log.d(TAG, "Song changed: ${song?.displayTitle}")

                    if (!_isAppInForeground.value) {
                        postNotificationUpdate()
                    }
                }
            }
        }
    }

    private fun performHeavyInitialization() {
        try {
            repository = MusicRepository(applicationContext)
            playerManager = MusicPlayerManager(applicationContext, repository)
            notificationManager = MusicNotificationManager(applicationContext, playerManager.mediaSession)
            notificationManager.isAppInForeground = { _isAppInForeground.value }
            setupMediaSessionCallback()
            isInitialized = true
            Log.d(TAG, "Heavy initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during heavy initialization: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            if (!isInitialized) {
                Log.w(TAG, "onStartCommand but init not complete, action=$action")
                Handler(Looper.getMainLooper()).post {
                    if (isInitialized) handleAction(action)
                }
                return START_STICKY
            }
            handleAction(action)
        }
        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            ACTION_PLAY -> { playerManager.togglePlayPause(); postNotificationUpdate() }
            ACTION_PAUSE -> { playerManager.pause(); postNotificationUpdate() }
            ACTION_NEXT -> { playerManager.next(); postNotificationUpdate() }
            ACTION_PREV -> { playerManager.previous(); postNotificationUpdate() }
            ACTION_CLOSE -> {
                playerManager.pause()
                stopPeriodicUpdates()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION") stopForeground(true)
                }
                notificationManager.cancelNotification()
                stopSelf()
            }
            ACTION_FAVORITE -> {
                playerManager.currentSong.value?.let { song ->
                    val newFavorite = !song.isFavorite

                    playerManager.updateSongFavorite(song.id, newFavorite)

                    serviceScope.launch(Dispatchers.IO) {
                        repository.toggleFavorite(song.id)
                    }

                    postNotificationUpdate()
                }
            }
            ACTION_PLAY_MODE -> { cyclePlayMode(); postNotificationUpdate() }
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean = super.onUnbind(intent)

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        stopPeriodicUpdates()
        _isServiceRunning.value = false
        _isAppInForeground.value = false
        if (::playerManager.isInitialized) playerManager.release()
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }

    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, MusicNotificationManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("")
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun setupMediaSessionCallback() {
        playerManager.mediaSession.setCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
            override fun onPlay() { playerManager.play(); postNotificationUpdate() }
            override fun onPause() { playerManager.pause(); postNotificationUpdate() }
            override fun onSkipToNext() { playerManager.next(); postNotificationUpdate() }
            override fun onSkipToPrevious() { playerManager.previous(); postNotificationUpdate() }

            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "SeekTo from system/notification: ${pos}ms")
                playerManager.seekTo(pos)

                notificationManager.onSeekTo(pos)
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                when (repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ONE -> {
                        playerManager.setRepeatMode(RepeatMode.ONE)
                        playerManager.setShuffleMode(ShuffleMode.OFF)
                    }
                    PlaybackStateCompat.REPEAT_MODE_ALL -> {
                        playerManager.setRepeatMode(RepeatMode.ALL)
                        playerManager.setShuffleMode(ShuffleMode.OFF)
                    }
                    else -> {
                        playerManager.setRepeatMode(RepeatMode.OFF)
                        playerManager.setShuffleMode(ShuffleMode.OFF)
                    }
                }
                postNotificationUpdate()
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
                    playerManager.setShuffleMode(ShuffleMode.ON)
                } else {
                    playerManager.setShuffleMode(ShuffleMode.OFF)
                }
                postNotificationUpdate()
            }

            override fun onStop() { playerManager.pause(); postNotificationUpdate() }
        })
    }

    private fun updatePeriodicProgress() {
        if (!isInitialized) return
        try {
            val song = playerManager.currentSong.value
            if (song == null) {

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                } catch (_: Exception) {}
                notificationManager.cancelNotification()
                return
            }

            val isPlaying = playerManager.isPlaying.value
            val position = playerManager.exoPlayer.currentPosition
            val dur = playerManager.exoPlayer.duration.coerceAtLeast(0L)
            val buffered = playerManager.exoPlayer.bufferedPosition
            val speed = playerManager.exoPlayer.playbackParameters.speed

            notificationManager.updateMediaSessionPlaybackState(
                isPlaying = isPlaying,
                currentPosition = position,
                bufferedPosition = buffered,
                speed = speed,
                duration = dur
            )

            if (_isAppInForeground.value) {
                notificationManager.cancelNotification()
                return
            }

            val songChanged = song.id != lastNotificationSongId
            val now = System.currentTimeMillis()
            val timeSinceRebuild = now - lastNotificationRebuildTime

            if (songChanged) {

                val notification = notificationManager.buildNotification(
                    song = song,
                    isPlaying = isPlaying,
                    repeatMode = playerManager.repeatMode.value,
                    shuffleMode = playerManager.shuffleMode.value,
                    currentPosition = position,
                    duration = dur,
                    isFavorite = song.isFavorite
                )
                startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, notification)
                lastNotificationSongId = song.id
                lastNotificationRebuildTime = now
            } else if (timeSinceRebuild >= NOTIFICATION_REBUILD_INTERVAL_MS) {

                val notification = notificationManager.buildNotification(
                    song = song,
                    isPlaying = isPlaying,
                    repeatMode = playerManager.repeatMode.value,
                    shuffleMode = playerManager.shuffleMode.value,
                    currentPosition = position,
                    duration = dur,
                    isFavorite = song.isFavorite
                )
                try {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(MusicNotificationManager.NOTIFICATION_ID, notification)
                } catch (_: Exception) {}
                lastNotificationRebuildTime = now
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in periodic progress update: ${e.message}")
        }
    }

    private fun postNotificationUpdate() {
        if (!isInitialized) return
        try {
            val song = playerManager.currentSong.value
            if (song == null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                } catch (_: Exception) {}
                notificationManager.cancelNotification()
                return
            }

            val isPlaying = playerManager.isPlaying.value
            val position = playerManager.exoPlayer.currentPosition
            val dur = playerManager.exoPlayer.duration.coerceAtLeast(0L)
            val buffered = playerManager.exoPlayer.bufferedPosition
            val speed = playerManager.exoPlayer.playbackParameters.speed

            notificationManager.updateMediaSessionPlaybackState(
                isPlaying = isPlaying,
                currentPosition = position,
                bufferedPosition = buffered,
                speed = speed,
                duration = dur
            )

            if (_isAppInForeground.value) {
                notificationManager.cancelNotification()
                return
            }

            val notification = notificationManager.buildNotification(
                song = song,
                isPlaying = isPlaying,
                repeatMode = playerManager.repeatMode.value,
                shuffleMode = playerManager.shuffleMode.value,
                currentPosition = position,
                duration = dur,
                isFavorite = song.isFavorite
            )
            startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, notification)
            lastNotificationSongId = song.id
            lastNotificationRebuildTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification update: ${e.message}")
        }
    }

    private fun stopPeriodicUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun cyclePlayMode() {
        val isShuffle = playerManager.shuffleMode.value == ShuffleMode.ON
        val isOneRepeat = playerManager.repeatMode.value == RepeatMode.ONE
        when {
            isOneRepeat -> {
                playerManager.setRepeatMode(RepeatMode.OFF)
                playerManager.setShuffleMode(ShuffleMode.OFF)
            }
            isShuffle -> {
                playerManager.setShuffleMode(ShuffleMode.OFF)
                playerManager.setRepeatMode(RepeatMode.ONE)
            }
            else -> {
                playerManager.setShuffleMode(ShuffleMode.ON)
            }
        }
    }
}
