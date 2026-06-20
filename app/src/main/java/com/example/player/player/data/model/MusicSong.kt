package com.example.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class MusicSong(
    @PrimaryKey
    val id: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0,
    val duration: Long = 0L,
    val path: String = "",
    val displayName: String = "",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val size: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val isFavorite: Boolean = false,
    val albumArtUri: String? = null,
    val embeddedLyrics: String? = null,
    val genre: String = "",
    val composer: String = "",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedTime: Long = 0L
) {
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "${size} B"
                size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
                else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
            }
        }

    val displayArtist: String
        get() = artist.ifEmpty { "未知艺术家" }

    val displayTitle: String
        get() = title.ifEmpty {
            displayName.removeSuffix(".mp3").removeSuffix(".flac").removeSuffix(".wav")
                .removeSuffix(".ogg").removeSuffix(".m4a").removeSuffix(".aac").removeSuffix(".wma")
                .ifEmpty { "未知歌曲" }
        }

    val displayAlbum: String
        get() = album.ifEmpty { "未知专辑" }

    val displaySubtitle: String
        get() = if (album.isNotEmpty()) "$album-${displayArtist}" else displayArtist
}
