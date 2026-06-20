package com.example.player.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.player.MainActivity
import com.example.player.R
import com.example.player.data.model.MusicSong
import com.example.player.data.model.RepeatMode
import com.example.player.data.model.ShuffleMode
import com.example.player.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) {

    companion object {
        private const val TAG = "MusicNotificationManager"
        const val CHANNEL_ID = "music_playback_channel"
        const val CHANNEL_NAME = "音乐播放"
        const val CHANNEL_DESC = "显示当前播放的音乐信息和控制"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PREV = "com.example.player.ACTION_PREV"
        const val ACTION_PLAY = "com.example.player.ACTION_PLAY"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"
        const val ACTION_CLOSE = "com.example.player.ACTION_CLOSE"
        const val ACTION_FAVORITE = "com.example.player.ACTION_FAVORITE"
        const val ACTION_PLAY_MODE = "com.example.player.ACTION_PLAY_MODE"
        const val EXTRA_OPEN_PLAYER = "open_player"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var currentLargeIcon: Bitmap? = null
    private var lastLoadedArtUri: String? = null
    private var albumArtLoadJob: Job? = null
    private val imageLoader = ImageLoader(context)

    var isAppInForeground: () -> Boolean = { true }

    private data class CachedNotificationParams(
        val song: MusicSong?,
        val isPlaying: Boolean,
        val repeatMode: RepeatMode,
        val shuffleMode: ShuffleMode,
        val isFavorite: Boolean
    )

    private var cachedParams: CachedNotificationParams? = null

    private var trackedSongId: Long = -1L

    private var lastSafeDuration: Long = 0L

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasSongChanged(song: MusicSong?): Boolean {
        return song?.id != trackedSongId
    }

    fun updateMediaSessionPlaybackState(
        isPlaying: Boolean,
        currentPosition: Long,
        bufferedPosition: Long,
        @Suppress("UNUSED_PARAMETER") speed: Float,
        duration: Long
    ) {
        try {

            if (duration > 0) {
                lastSafeDuration = duration
            }

            val safePosition = currentPosition.coerceAtLeast(0L)

            val displaySpeed = if (isPlaying) 1.0f else 0.0f

            val state = if (isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                )
                .setState(state, safePosition, displaySpeed, SystemClock.elapsedRealtime())
                .setBufferedPosition(bufferedPosition.coerceAtLeast(0L))
                .build()

            mediaSession.setPlaybackState(playbackState)

            if (lastSafeDuration > 0) {
                val currentMetadata = mediaSession.controller?.metadata
                val currentDuration = currentMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
                if (currentDuration != lastSafeDuration) {
                    updateMediaSessionMetadataWithDuration(lastSafeDuration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playback state: ${e.message}")
        }
    }

    fun onSeekTo(position: Long) {
        if (position >= 0) {
            Log.d(TAG, "onSeekTo: position=${position}ms")

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, position, 1.0f, SystemClock.elapsedRealtime())
                .build()

            mediaSession.setPlaybackState(playbackState)
        }
    }

    fun buildNotification(
        song: MusicSong?,
        isPlaying: Boolean,
        repeatMode: RepeatMode,
        shuffleMode: ShuffleMode,
        currentPosition: Long,
        duration: Long,
        isFavorite: Boolean
    ): Notification {
        val songId = song?.id ?: -1L

        if (songId != trackedSongId) {
            trackedSongId = songId
            lastSafeDuration = 0L
        }

        if (duration > 0) {
            lastSafeDuration = duration
        }

        val safeDuration = lastSafeDuration
        val safePosition = currentPosition.coerceAtLeast(0L)

        cachedParams = CachedNotificationParams(
            song, isPlaying, repeatMode, shuffleMode, isFavorite
        )

        loadAlbumArt(song)

        val title = song?.displayTitle ?: "音乐"
        val artist = song?.displaySubtitle ?: "未在播放"

        val timeText = if (safeDuration > 0) "${formatTime(safePosition)} / ${formatTime(safeDuration)}" else ""

        val (playModeIcon, playModeLabel) = getPlayModeIconAndLabel(repeatMode, shuffleMode)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(timeText)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(getServicePendingIntent(ACTION_CLOSE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            .setLargeIcon(currentLargeIcon ?: createDefaultAlbumArt())
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 4)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getServicePendingIntent(ACTION_CLOSE))
            )

            .apply {
                val durSec = (safeDuration / 1000).toInt().coerceAtLeast(0)
                val posSec = (safePosition / 1000).toInt().coerceAtLeast(0)
                if (durSec > 0) {
                    setProgress(durSec, posSec, false)
                } else {
                    setProgress(0, 0, true)
                }
            }

            .addAction(
                playModeIcon,
                playModeLabel,
                getServicePendingIntent(ACTION_PLAY_MODE)
            )

            .addAction(
                R.drawable.ic_prev,
                "上一首",
                getServicePendingIntent(ACTION_PREV)
            )

            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "暂停" else "播放",
                getServicePendingIntent(ACTION_PLAY)
            )

            .addAction(
                R.drawable.ic_next,
                "下一首",
                getServicePendingIntent(ACTION_NEXT)
            )

            .addAction(
                if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                if (isFavorite) "取消收藏" else "收藏",
                getServicePendingIntent(ACTION_FAVORITE)
            )

        updateMediaSessionMetadata(song, safeDuration)

        return builder.build()
    }

    fun updateNotification(
        song: MusicSong?,
        isPlaying: Boolean,
        repeatMode: RepeatMode,
        shuffleMode: ShuffleMode,
        currentPosition: Long,
        duration: Long,
        isFavorite: Boolean
    ) {
        val notification = buildNotification(
            song, isPlaying, repeatMode, shuffleMode,
            currentPosition, duration, isFavorite
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun getPlayModeIconAndLabel(
        repeatMode: RepeatMode,
        shuffleMode: ShuffleMode
    ): Pair<Int, String> {
        return when {
            shuffleMode == ShuffleMode.ON ->
                R.drawable.ic_shuffle to "随机播放"
            repeatMode == RepeatMode.ONE ->
                R.drawable.ic_repeat_one to "单曲循环"
            else ->
                R.drawable.ic_repeat to "顺序播放"
        }
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArt(song: MusicSong?) {
        val uri = song?.albumArtUri
        if (uri == null) {
            currentLargeIcon = createDefaultAlbumArt()
            lastLoadedArtUri = null
            return
        }

        if (uri == lastLoadedArtUri) return

        albumArtLoadJob?.cancel()

        albumArtLoadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(400, 400)
                    .build()

                val result = imageLoader.execute(request)
                val drawable = result.drawable
                if (drawable != null && isActive) {
                    val bitmap = when (drawable) {
                        is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                        else -> {
                            val bmp = Bitmap.createBitmap(
                                drawable.intrinsicWidth.coerceAtLeast(1),
                                drawable.intrinsicHeight.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                    }
                    if (bitmap != null && isActive) {
                        currentLargeIcon = bitmap
                        lastLoadedArtUri = uri

                        if (isAppInForeground()) {
                            return@launch
                        }

                        cachedParams?.let { params ->

                            val currentPos = try {
                                (mediaSession.controller?.playbackState?.position ?: 0L)
                                    .coerceAtLeast(0L)
                            } catch (_: Exception) { 0L }
                            val notification = buildNotification(
                                params.song,
                                params.isPlaying,
                                params.repeatMode,
                                params.shuffleMode,
                                currentPos,
                                lastSafeDuration,
                                params.isFavorite
                            )
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        }
                    }
                } else if (isActive) {
                    currentLargeIcon = createDefaultAlbumArt()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error loading album art: ${e.message}")
                currentLargeIcon = createDefaultAlbumArt()
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun createDefaultAlbumArt(): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val themeColor = com.example.player.ui.theme.getThemeColorOption(
            com.example.player.ui.theme.ThemeColorPreference.getThemeColorIndexSync(context)
        ).primary

        val bgR = themeColor.red * 0.08f + 1f * 0.92f
        val bgG = themeColor.green * 0.08f + 1f * 0.92f
        val bgB = themeColor.blue * 0.08f + 1f * 0.92f
        val bgColor = (0xFF shl 24) or
                ((bgR.coerceIn(0f, 1f) * 255).toInt() shl 16) or
                ((bgG.coerceIn(0f, 1f) * 255).toInt() shl 8) or
                (bgB.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawColor(bgColor)

        val paint = android.graphics.Paint().apply {
            color = ((themeColor.alpha * 255).toInt() shl 24) or
                    ((themeColor.red * 255).toInt() shl 16) or
                    ((themeColor.green * 255).toInt() shl 8) or
                    (themeColor.blue * 255).toInt()
            textSize = 48f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val fontMetrics = paint.fontMetrics
        val centerY = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("♪", size / 2f, centerY, paint)
        return bitmap
    }

    private fun updateMediaSessionMetadata(song: MusicSong?, duration: Long) {
        try {
            val metadata = MediaMetadataCompat.Builder().apply {
                song?.let {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.displayTitle)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.displaySubtitle)
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.displaySubtitle)
                    putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    currentLargeIcon?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                }
            }.build()
            mediaSession.setMetadata(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media session metadata: ${e.message}")
        }
    }

    private fun updateMediaSessionMetadataWithDuration(duration: Long) {
        try {
            val existingMetadata = mediaSession.controller?.metadata
            val metadata = MediaMetadataCompat.Builder().apply {
                existingMetadata?.let { meta ->
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "")
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "")
                    val art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    if (art != null) {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    }
                }
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            }.build()
            mediaSession.setMetadata(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media session metadata duration: ${e.message}")
        }
    }
}
