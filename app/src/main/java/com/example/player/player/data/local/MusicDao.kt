package com.example.player.data.local

import androidx.room.*
import com.example.player.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<MusicSong>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsOnce(): List<MusicSong>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<MusicSong>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): MusicSong?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongByIdFlow(id: Long): Flow<MusicSong?>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY trackNumber ASC")
    fun getSongsByAlbum(albumId: Long): Flow<List<MusicSong>>

    @Query("SELECT * FROM songs WHERE artist LIKE '%' || :artist || '%' ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<MusicSong>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<MusicSong>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayedSongs(limit: Int = 20): List<MusicSong>

    @Query("SELECT * FROM songs ORDER BY lastPlayedTime DESC LIMIT :limit")
    suspend fun getRecentlyPlayedSongs(limit: Int = 20): List<MusicSong>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    suspend fun getRecentlyAddedSongs(limit: Int = 20): List<MusicSong>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAddedSongsFlow(limit: Int = 20): Flow<List<MusicSong>>

    @Query("SELECT * FROM songs ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSongs(limit: Int = 20): List<MusicSong>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<MusicSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: MusicSong): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<MusicSong>)

    @Update
    suspend fun updateSong(song: MusicSong)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedTime = :playTime WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, playTime: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET skipCount = skipCount + 1 WHERE id = :songId")
    suspend fun incrementSkipCount(songId: Long)

    @Delete
    suspend fun deleteSong(song: MusicSong)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.orderIndex ASC")
    fun getSongsInPlaylist(playlistId: Long): Flow<List<MusicSong>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(history: PlayHistory)

    @Query("SELECT s.* FROM songs s INNER JOIN play_history ph ON s.id = ph.songId ORDER BY ph.playedAt DESC LIMIT :limit")
    fun getPlayHistory(limit: Int = 50): Flow<List<MusicSong>>

    @Query("DELETE FROM play_history")
    suspend fun clearPlayHistory()

    @Query("SELECT COUNT(*) FROM songs WHERE isFavorite = 1")
    fun getFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlists")
    fun getPlaylistCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getTotalSongCount(): Int

    @Query("SELECT SUM(duration) FROM songs")
    suspend fun getTotalDuration(): Long?
}
