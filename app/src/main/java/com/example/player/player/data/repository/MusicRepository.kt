package com.example.player.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.player.data.local.AppDatabase
import com.example.player.data.local.MusicDao
import com.example.player.data.model.MusicSong
import com.example.player.data.model.PlayHistory
import com.example.player.data.model.Playlist
import com.example.player.data.model.PlaylistSongCrossRef
import com.example.player.ui.util.LyricsExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {

    private val dao: MusicDao = AppDatabase.getDatabase(context).musicDao()

    companion object {
        private const val TAG = "MusicRepository"
    }

    val allSongs: Flow<List<MusicSong>> = dao.getAllSongs()

    val favoriteSongs: Flow<List<MusicSong>> = dao.getFavoriteSongs()

    fun getSongById(id: Long): Flow<MusicSong?> = dao.getSongByIdFlow(id)

    suspend fun getSongByIdOnce(id: Long): MusicSong? = dao.getSongById(id)

    suspend fun getAllSongsOnce(): List<MusicSong> = dao.getAllSongsOnce()

    fun searchSongs(query: String): Flow<List<MusicSong>> = dao.searchSongs(query)

    val songCount: Flow<Int> = dao.getSongCount()

    val favoriteCount: Flow<Int> = dao.getFavoriteCount()

    val allArtists: Flow<List<String>> = dao.getAllArtists()

    val allAlbums: Flow<List<String>> = dao.getAllAlbums()

    val allGenres: Flow<List<String>> = dao.getAllGenres()

    fun getSongsByAlbum(albumId: Long): Flow<List<MusicSong>> = dao.getSongsByAlbum(albumId)

    fun getSongsByArtist(artist: String): Flow<List<MusicSong>> = dao.getSongsByArtist(artist)

    fun getSongsByGenre(genre: String): Flow<List<MusicSong>> = dao.getSongsByGenre(genre)

    suspend fun scanLocalMusic(): Int = withContext(Dispatchers.IO) {
        val songs = mutableListOf<MusicSong>()
        val existingIds = mutableSetOf<Long>()

        val existingSongs = dao.getAllSongsOnce()
        existingIds.addAll(existingSongs.map { it.id })

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val path = cursor.getString(dataColumn) ?: ""

                        if (path.isEmpty() || !File(path).exists()) {
                            Log.d(TAG, "Skipping ghost file: id=$id, path=$path (not exists)")
                            continue
                        }
                        val song = MusicSong(
                            id = id,
                            title = cursor.getString(titleColumn) ?: "",
                            artist = cursor.getString(artistColumn) ?: "",
                            album = cursor.getString(albumColumn) ?: "",
                            albumId = cursor.getLong(albumIdColumn),
                            duration = cursor.getLong(durationColumn),
                            path = path,
                            displayName = cursor.getString(displayNameColumn) ?: "",
                            dateAdded = cursor.getLong(dateAddedColumn) * 1000,
                            dateModified = cursor.getLong(dateModifiedColumn) * 1000,
                            size = cursor.getLong(sizeColumn),
                            trackNumber = cursor.getInt(trackColumn),
                            year = cursor.getInt(yearColumn),
                            isFavorite = existingSongs.find { it.id == id }?.isFavorite ?: false,
                            albumArtUri = getAlbumArtUri(cursor.getLong(albumIdColumn)),
                            playCount = existingSongs.find { it.id == id }?.playCount ?: 0,
                            skipCount = existingSongs.find { it.id == id }?.skipCount ?: 0,
                            lastPlayedTime = existingSongs.find { it.id == id }?.lastPlayedTime ?: 0L
                        )
                        songs.add(song)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading song: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning music: ${e.message}", e)
        }

        val currentIds = songs.map { it.id }.toSet()
        val toRemove = existingIds - currentIds
        toRemove.forEach { dao.deleteSongById(it) }

        val ghostSongsInDb = existingSongs.filter { existing ->
            existing.id !in currentIds || !File(existing.path).exists()
        }
        ghostSongsInDb.forEach { ghost ->
            Log.d(TAG, "Removing ghost song from DB: ${ghost.title} (path=${ghost.path})")
            dao.deleteSongById(ghost.id)
        }

        dao.insertSongs(songs)

        songs.forEach { song ->
            try {
                extractGenreOnly(song)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting genre for ${song.title}: ${e.message}")
            }
        }

        val totalRemoved = toRemove.size + ghostSongsInDb.size
        Log.d(TAG, "Scan complete. Found ${songs.size} songs, removed $totalRemoved (${toRemove.size} from MediaStore + ${ghostSongsInDb.size} ghost files)")
        songs.size
    }

    private fun getAlbumArtUri(albumId: Long): String? {
        return try {
            val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

            context.contentResolver.openInputStream(albumArtUri)?.close()
            albumArtUri.toString()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractEmbeddedMetadata(song: MusicSong) {
        try {
            val file = File(song.path)
            if (!file.exists()) return

            val genreCursor = context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", song.id),
                arrayOf(MediaStore.Audio.Genres.Members.GENRE_ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )
            genreCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val genreIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                    if (genreIndex >= 0) {
                        val genre = cursor.getString(genreIndex)
                        if (!genre.isNullOrBlank()) {
                            val updatedSong = song.copy(genre = genre)
                            dao.updateSong(updatedSong)
                        }
                    }
                }
            }

            val lyrics = LyricsExtractor.getLyricsContent(song.path)
            if (lyrics != null) {
                val existing = dao.getSongById(song.id) ?: return
                dao.updateSong(existing.copy(embeddedLyrics = lyrics))
                Log.d(TAG, "Embedded lyrics extracted for: ${song.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata: ${e.message}")
        }
    }

    private suspend fun extractGenreOnly(song: MusicSong) {
        try {
            val genreCursor = context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", song.id),
                arrayOf(MediaStore.Audio.Genres.Members.GENRE_ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )
            genreCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val genreIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                    if (genreIndex >= 0) {
                        val genre = cursor.getString(genreIndex)
                        if (!genre.isNullOrBlank()) {
                            val existing = dao.getSongById(song.id) ?: return
                            dao.updateSong(existing.copy(genre = genre))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting genre: ${e.message}")
        }
    }

    suspend fun toggleFavorite(songId: Long) {
        val song = dao.getSongById(songId) ?: return
        dao.updateFavoriteStatus(songId, !song.isFavorite)
    }

    suspend fun isFavorite(songId: Long): Boolean {
        return dao.getSongById(songId)?.isFavorite ?: false
    }

    suspend fun deleteSongById(songId: Long) {
        dao.deleteSongById(songId)
    }

    suspend fun recordPlay(songId: Long, completedDuration: Long = 0L, isCompleted: Boolean = false) {
        dao.incrementPlayCount(songId)
        dao.insertPlayHistory(
            PlayHistory(
                songId = songId,
                completedDuration = completedDuration,
                isCompleted = isCompleted
            )
        )
    }

    suspend fun recordSkip(songId: Long) {
        dao.incrementSkipCount(songId)
    }

    val allPlaylists: Flow<List<Playlist>> = dao.getAllPlaylists()

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return dao.insertPlaylist(
            Playlist(
                name = name,
                description = description,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylistById(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val count = dao.getPlaylistSongCount(playlistId)
        dao.addSongToPlaylist(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                orderIndex = count
            )
        )

        val playlist = dao.getPlaylistById(playlistId) ?: return
        dao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis(), songCount = count + 1))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        dao.removeSongFromPlaylist(playlistId, songId)
        val count = dao.getPlaylistSongCount(playlistId)
        val playlist = dao.getPlaylistById(playlistId) ?: return
        dao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis(), songCount = count))
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<MusicSong>> = dao.getSongsInPlaylist(playlistId)

    suspend fun getMostPlayedSongs(limit: Int = 20): List<MusicSong> = dao.getMostPlayedSongs(limit)

    suspend fun getRecentlyPlayedSongs(limit: Int = 20): List<MusicSong> = dao.getRecentlyPlayedSongs(limit)

    suspend fun getRecentlyAddedSongs(limit: Int = 20): List<MusicSong> = dao.getRecentlyAddedSongs(limit)

    fun getRecentlyAddedSongsFlow(limit: Int = 10): Flow<List<MusicSong>> =
        dao.getRecentlyAddedSongsFlow(limit)

    suspend fun getRandomSongs(limit: Int = 20): List<MusicSong> = dao.getRandomSongs(limit)

    suspend fun getTotalStats(): Triple<Int, Long?, Int> {
        val count = dao.getTotalSongCount()
        val totalDuration = dao.getTotalDuration()
        val favCount = dao.getFavoriteCount()
        return Triple(count, totalDuration, 0)
    }
}
