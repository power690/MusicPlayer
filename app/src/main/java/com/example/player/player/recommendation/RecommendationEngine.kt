package com.example.player.recommendation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.player.data.model.MusicSong
import com.example.player.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.random.Random

class RecommendationEngine(
    private val context: Context,
    private val repository: MusicRepository
) {

    companion object {
        private const val TAG = "RecommendationEngine"
        private const val PREFS_NAME = "recommendation_prefs"
        private const val KEY_LAST_REFRESH = "last_refresh_time"
        private const val KEY_REFRESH_INTERVAL = 30 * 60 * 1000L

        private const val WEIGHT_PLAY_COUNT = 0.20
        private const val WEIGHT_RECENCY = 0.20
        private const val WEIGHT_COMPLETION = 0.15
        private const val WEIGHT_ARTIST_AFFINITY = 0.15
        private const val WEIGHT_GENRE_AFFINITY = 0.10
        private const val WEIGHT_TIME_BASED = 0.05
        private const val WEIGHT_FAVORITE = 0.05
        private const val WEIGHT_EXPLORATION = 0.10

        private const val DECAY_FACTOR = 0.95
        private const val MAX_SCORE = 100.0
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var cachedSuggestions: List<MusicSong> = emptyList()
    private var cachedFeatured: List<MusicSong> = emptyList()
    private var lastRefreshTime: Long = 0

    suspend fun getSuggestions(limit: Int = 20): List<MusicSong> {

        if (shouldRefresh() || cachedSuggestions.isEmpty()) {
            refreshRecommendations()
        }
        return cachedSuggestions.take(limit)
    }

    suspend fun getFeaturedSongs(limit: Int = 20): List<MusicSong> {
        if (shouldRefresh() || cachedFeatured.isEmpty()) {
            refreshRecommendations()
        }
        return cachedFeatured.take(limit)
    }

    suspend fun getSimilarSongs(currentSong: MusicSong, limit: Int = 10): List<MusicSong> {
        return withContext(Dispatchers.IO) {
            val allSongs = repository.getAllSongsOnce()
            allSongs
                .filter { it.id != currentSong.id }
                .map { song ->
                    val score = calculateSimilarityScore(currentSong, song)
                    song to score
                }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

    suspend fun getDailyPicks(limit: Int = 30): List<MusicSong> {
        return withContext(Dispatchers.IO) {
            val allSongs = repository.getAllSongsOnce()
            if (allSongs.isEmpty()) return@withContext emptyList()

            val userProfile = buildUserProfile(allSongs)
            val timeWeight = calculateTimeWeight()
            val scoredSongs = allSongs.map { song ->
                val baseScore = calculateWeightedScore(song, userProfile)
                val timeBoost = if (timeWeight.containsKey(song.genre)) {
                    timeWeight[song.genre]!! * 10.0
                } else 0.0
                song to (baseScore + timeBoost + Random.nextDouble(0.0, MAX_SCORE * WEIGHT_EXPLORATION))
            }

            scoredSongs
                .sortedByDescending { it.second }
                .take(limit)
                .shuffled()
                .map { it.first }
        }
    }

    suspend fun refreshRecommendations() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Refreshing recommendations...")

            val allSongs = repository.getAllSongsOnce()
            if (allSongs.isEmpty()) {
                cachedSuggestions = emptyList()
                cachedFeatured = emptyList()
                lastRefreshTime = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_REFRESH, lastRefreshTime).apply()
                return@withContext
            }

            val userProfile = buildUserProfile(allSongs)

            val scoredSongs = allSongs.map { song ->
                val score = calculateWeightedScore(song, userProfile)
                song to score
            }

            cachedSuggestions = scoredSongs
                .sortedByDescending { it.second }
                .take(30)
                .shuffled(Random(System.currentTimeMillis()))
                .map { it.first }

            cachedFeatured = buildFeaturedList(allSongs, userProfile)

            lastRefreshTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_REFRESH, lastRefreshTime).apply()

            Log.d(TAG, "Recommendations refreshed: ${cachedSuggestions.size} suggestions, ${cachedFeatured.size} featured")
        }
    }

    private suspend fun buildUserProfile(allSongs: List<MusicSong>): UserProfile {
        val totalPlays = allSongs.sumOf { it.playCount }.coerceAtLeast(1)

        val artistPlays = mutableMapOf<String, Int>()
        val genrePlays = mutableMapOf<String, Int>()
        val albumPlays = mutableMapOf<String, Int>()

        allSongs.forEach { song ->
            if (song.playCount > 0) {
                artistPlays[song.artist] = (artistPlays[song.artist] ?: 0) + song.playCount
                if (song.genre.isNotEmpty()) {
                    genrePlays[song.genre] = (genrePlays[song.genre] ?: 0) + song.playCount
                }
                albumPlays[song.album] = (albumPlays[song.album] ?: 0) + song.playCount
            }
        }

        return UserProfile(
            totalPlays = totalPlays,
            artistPreferences = artistPlays.mapValues { (_, count) -> count.toDouble() / totalPlays },
            genrePreferences = genrePlays.mapValues { (_, count) -> count.toDouble() / totalPlays },
            albumPreferences = albumPlays.mapValues { (_, count) -> count.toDouble() / totalPlays },
            favoriteArtistCount = artistPlays.size,
            favoriteGenreCount = genrePlays.size
        )
    }

    private fun calculateWeightedScore(song: MusicSong, profile: UserProfile): Double {
        var score = 0.0

        val playScore = minOf(song.playCount * 5.0, MAX_SCORE)
        score += playScore * WEIGHT_PLAY_COUNT

        val recencyScore = calculateRecencyScore(song.lastPlayedTime)
        score += recencyScore * WEIGHT_RECENCY

        val totalInteractions = (song.playCount + song.skipCount).coerceAtLeast(1)
        val completionRate = song.playCount.toDouble() / totalInteractions
        score += completionRate * MAX_SCORE * WEIGHT_COMPLETION

        val artistScore = (profile.artistPreferences[song.artist] ?: 0.0) * MAX_SCORE * 10
        score += minOf(artistScore, MAX_SCORE) * WEIGHT_ARTIST_AFFINITY

        val genreScore = (profile.genrePreferences[song.genre] ?: 0.0) * MAX_SCORE * 10
        score += minOf(genreScore, MAX_SCORE) * WEIGHT_GENRE_AFFINITY

        val timeScore = calculateTimeWeight().getOrDefault(song.genre, 0.5) * MAX_SCORE
        score += timeScore * WEIGHT_TIME_BASED

        if (song.isFavorite) {
            score += MAX_SCORE * WEIGHT_FAVORITE
        }

        score += Random.nextDouble(0.0, MAX_SCORE) * WEIGHT_EXPLORATION

        return score
    }

    private fun calculateRecencyScore(lastPlayedTime: Long): Double {
        if (lastPlayedTime <= 0) return 0.0

        val now = System.currentTimeMillis()
        val hoursSincePlayed = ((now - lastPlayedTime) / (1000 * 60 * 60)).coerceAtLeast(0).toInt()

        val halfLife = 168.0
        return MAX_SCORE * Math.pow(DECAY_FACTOR, hoursSincePlayed / halfLife)
    }

    private fun calculateTimeWeight(): Map<String, Double> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val weightMap = mutableMapOf<String, Double>()

        val timeMultiplier = when (hour) {
            in 6..11 -> 1.0
            in 12..17 -> 0.9
            in 18..21 -> 0.8
            else -> 0.7
        }

        weightMap["流行"] = timeMultiplier
        weightMap["摇滚"] = if (hour in 6..18) 1.1 else 0.6
        weightMap["古典"] = if (hour in 20..23 || hour in 0..5) 1.2 else 0.7
        weightMap["电子"] = if (hour in 18..23) 1.1 else 0.8
        weightMap["民谣"] = if (hour in 18..22) 1.1 else 0.9
        weightMap["爵士"] = if (hour in 20..23 || hour in 0..5) 1.2 else 0.7
        weightMap["R&B"] = timeMultiplier
        weightMap["嘻哈"] = if (hour in 12..22) 1.0 else 0.7
        weightMap["轻音乐"] = if (hour in 20..23 || hour in 0..5) 1.2 else 0.8
        weightMap[""] = timeMultiplier

        return weightMap
    }

    private fun calculateSimilarityScore(song1: MusicSong, song2: MusicSong): Double {
        var similarity = 0.0
        var factors = 0

        if (song1.artist == song2.artist) {
            similarity += 40.0
        } else if (song1.artist.isNotBlank() && song2.artist.isNotBlank()) {

            if (song1.artist.contains(song2.artist) || song2.artist.contains(song1.artist)) {
                similarity += 25.0
            }
        }
        factors++

        if (song1.albumId == song2.albumId && song1.albumId != 0L) {
            similarity += 30.0
        }
        factors++

        if (song1.genre == song2.genre && song1.genre.isNotEmpty()) {
            similarity += 20.0
        }
        factors++

        val durationDiff = kotlin.math.abs(song1.duration - song2.duration)
        if (durationDiff < 120000) {
            similarity += 10.0 * (1.0 - durationDiff / 120000.0)
        }
        factors++

        similarity += Random.nextDouble(0.0, 10.0)

        return similarity
    }

    private fun buildFeaturedList(
        allSongs: List<MusicSong>,
        profile: UserProfile
    ): List<MusicSong> {
        if (allSongs.isEmpty()) return emptyList()

        val featured = mutableListOf<MusicSong>()
        val usedIds = mutableSetOf<Long>()

        val mostPlayed = allSongs
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(5)
        featured.addAll(mostPlayed)
        usedIds.addAll(mostPlayed.map { it.id })

        val recentAdded = allSongs
            .filter { it.id !in usedIds }
            .sortedByDescending { it.dateAdded }
            .take(5)
        featured.addAll(recentAdded)
        usedIds.addAll(recentAdded.map { it.id })

        val favorites = allSongs
            .filter { it.isFavorite && it.id !in usedIds }
            .shuffled()
            .take(3)
        featured.addAll(favorites)
        usedIds.addAll(favorites.map { it.id })

        profile.artistPreferences.entries
            .sortedByDescending { it.value }
            .take(5)
            .forEach { (artist, _) ->
                val songFromArtist = allSongs
                    .filter { it.artist == artist && it.id !in usedIds }
                    .shuffled()
                    .firstOrNull()
                if (songFromArtist != null) {
                    featured.add(songFromArtist)
                    usedIds.add(songFromArtist.id)
                }
            }

        val remaining = allSongs
            .filter { it.id !in usedIds }
            .shuffled(Random(System.currentTimeMillis()))
            .take((30 - featured.size).coerceAtLeast(0))
        featured.addAll(remaining)

        return featured.shuffled(Random(System.currentTimeMillis()))
    }

    private fun shouldRefresh(): Boolean {
        val lastRefresh = prefs.getLong(KEY_LAST_REFRESH, 0)
        return System.currentTimeMillis() - lastRefresh > KEY_REFRESH_INTERVAL
    }

    suspend fun forceRefresh() {
        refreshRecommendations()
    }

    data class UserProfile(
        val totalPlays: Int,
        val artistPreferences: Map<String, Double>,
        val genrePreferences: Map<String, Double>,
        val albumPreferences: Map<String, Double>,
        val favoriteArtistCount: Int,
        val favoriteGenreCount: Int
    )
}
