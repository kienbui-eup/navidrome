package me.troly.nhac.playback

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.troly.nhac.data.subsonic.Song
import me.troly.nhac.data.subsonic.SubsonicRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ── LAST.FM API MODELS ──────────────────────────────────────────────────────

@Serializable
data class LastfmSimilarTracksResponse(val similartracks: LastfmSimilarTracks? = null)

@Serializable
data class LastfmSimilarTracks(val track: List<LastfmTrack> = emptyList())

@Serializable
data class LastfmTrack(
    val name: String = "",
    val playcount: Int? = null,
    val mbid: String? = null,
    val match: Double? = null,
    val url: String? = null,
    val artist: LastfmArtist? = null,
)

@Serializable
data class LastfmArtist(
    val name: String = "",
    val mbid: String? = null,
    val url: String? = null
)

@Serializable
data class LastfmTopTracksResponse(val tracks: LastfmTopTracks? = null)

@Serializable
data class LastfmTopTracks(val track: List<LastfmTrack> = emptyList())

// ── RETROFIT SERVICE INTERFACE ──────────────────────────────────────────────

interface LastfmApi {
    @GET("2.0/")
    suspend fun getSimilarTracks(
        @Query("method") method: String = "track.getSimilar",
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 20
    ): LastfmSimilarTracksResponse

    @GET("2.0/")
    suspend fun getTopTracks(
        @Query("method") method: String = "chart.getTopTracks",
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30
    ): LastfmTopTracksResponse
}

// ── RECOMMENDATION MANAGER ──────────────────────────────────────────────────

class RecommendationManager(context: Context) {

    private val sharedPrefs = context.getSharedPreferences("trolynhac_recommend", Context.MODE_PRIVATE)

    // User preference for Auto-Radio / Autoplay (infinite music queue)
    private val _autoRadioEnabled = MutableStateFlow(sharedPrefs.getBoolean("autoRadioEnabled", true))
    val autoRadioEnabled = _autoRadioEnabled.asStateFlow()

    fun setAutoRadioEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("autoRadioEnabled", enabled).apply()
        _autoRadioEnabled.value = enabled
    }

    private val apiKey = "482a0b181db86290875e533e429990b7" // Standard open-source client key

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val lastfmApi: LastfmApi by lazy {
        val http = OkHttpClient.Builder().build()
        Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LastfmApi::class.java)
    }

    /**
     * String normalization for robust matching between Last.fm and Navidrome metadata.
     * Cleans parentheses, brackets, special characters, and double-spacing.
     */
    private fun String.normalize(): String {
        return this.lowercase()
            .replace(Regex("\\(.*?\\)"), "") // Remove "(Live)", "(2024 Remaster)"
            .replace(Regex("\\[.*?\\]"), "") // Remove bracketed content
            .replace(Regex("[^a-z0-9\\s]"), "") // Strip punctuation
            .replace(Regex("\\s+"), " ") // Trim whitespace duplicates
            .trim()
    }

    /**
     * Recommends similar tracks in the user's local Navidrome library based on the currently playing song.
     * Combines Last.fm cloud similarities with local fuzzy matching and resilient local fallback.
     */
    suspend fun recommendSimilar(
        currentSongTitle: String,
        currentArtistName: String,
        currentArtistId: String?,
        repo: SubsonicRepository
    ): List<Song> = withContext(Dispatchers.IO) {
        if (currentSongTitle.isBlank()) return@withContext emptyList()
        val matchedSongs = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()

        try {
            Log.d("RecManager", "Fetching similarities from Last.fm for: $currentSongTitle - $currentArtistName")
            val response = lastfmApi.getSimilarTracks(
                artist = currentArtistName,
                track = currentSongTitle,
                apiKey = apiKey
            )
            val recommendations = response.similartracks?.track ?: emptyList()
            Log.d("RecManager", "Last.fm returned ${recommendations.size} recommendations")

            for (rec in recommendations) {
                val recTitle = rec.name
                val recArtist = rec.artist?.name ?: ""
                if (recTitle.isBlank()) continue

                // Search in local Navidrome library using track name
                val searchResult = try {
                    repo.search(recTitle)
                } catch (e: Exception) {
                    null
                }
                
                if (searchResult != null && searchResult.song.isNotEmpty()) {
                    // Normalize target title and artist
                    val normalizedTargetTitle = recTitle.normalize()
                    val normalizedTargetArtist = recArtist.normalize()

                    // Match search results fuzzy
                    val localMatch = searchResult.song.find { localSong ->
                        val localTitleNorm = localSong.title.normalize()
                        val localArtistNorm = (localSong.artist ?: "").normalize()
                        
                        // Exact matching or high substring similarity
                        (localTitleNorm == normalizedTargetTitle && 
                         (localArtistNorm.contains(normalizedTargetArtist) || localArtistNorm == normalizedTargetArtist))
                    }

                    if (localMatch != null && !seenIds.contains(localMatch.id)) {
                        matchedSongs.add(localMatch)
                        seenIds.add(localMatch.id)
                        Log.d("RecManager", "Matched recommendation: ${localMatch.title} by ${localMatch.artist}")
                    }
                }
                
                // Limit matches to 10 for performance and UI layout harmony
                if (matchedSongs.size >= 10) break
            }
        } catch (e: Exception) {
            Log.e("RecManager", "Last.fm API failure, falling back to local recommendation", e)
        }

        // Resilient Fallback 1: If Last.fm is empty or failed, fetch other tracks from the same artist
        if (matchedSongs.isEmpty() && !currentArtistId.isNullOrBlank()) {
            try {
                Log.d("RecManager", "Fallback: Querying local songs for artist ID: $currentArtistId")
                val artistDetail = repo.artist(currentArtistId)
                val fallbackSongs = artistDetail?.album?.flatMap { album ->
                    repo.album(album.id)?.song.orEmpty()
                }?.filter { 
                    it.title.normalize() != currentSongTitle.normalize() && !seenIds.contains(it.id)
                }?.shuffled()?.take(6).orEmpty()

                matchedSongs.addAll(fallbackSongs)
                fallbackSongs.forEach { seenIds.add(it.id) }
                Log.d("RecManager", "Artist fallback found ${fallbackSongs.size} tracks")
            } catch (e: Exception) {
                Log.e("RecManager", "Artist fallback failed", e)
            }
        }

        // Resilient Fallback 2: If everything else fails, fetch a few random albums/songs to keep the music playing
        if (matchedSongs.isEmpty()) {
            try {
                Log.d("RecManager", "Fallback: Querying random songs from the server")
                val randomAlbums = repo.albums("random", 5)
                val fallbackSongs = randomAlbums.flatMap { album ->
                    repo.album(album.id)?.song.orEmpty()
                }.filter { 
                    it.title.normalize() != currentSongTitle.normalize() && !seenIds.contains(it.id)
                }.shuffled().take(6)

                matchedSongs.addAll(fallbackSongs)
                Log.d("RecManager", "Random fallback found ${fallbackSongs.size} tracks")
            } catch (e: Exception) {
                Log.e("RecManager", "Random fallback failed", e)
            }
        }

        return@withContext matchedSongs
    }

    /**
     * Fetches globally trending tracks from Last.fm and cross-references them with the user's
     * local Navidrome library to return a list of high-quality local files that are "Đang Hot".
     */
    suspend fun getTrendingLocalTracks(repo: SubsonicRepository): List<Song> = withContext(Dispatchers.IO) {
        val trendingSongs = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()

        try {
            val response = lastfmApi.getTopTracks(apiKey = apiKey, limit = 30)
            val topTracks = response.tracks?.track ?: emptyList()

            for (track in topTracks) {
                val title = track.name
                val artist = track.artist?.name ?: ""
                
                val searchResult = try { repo.search(title) } catch (e: Exception) { null }
                if (searchResult != null && searchResult.song.isNotEmpty()) {
                    val targetTitleNorm = title.normalize()
                    val targetArtistNorm = artist.normalize()

                    val match = searchResult.song.find { localSong ->
                        val localTitleNorm = localSong.title.normalize()
                        val localArtistNorm = (localSong.artist ?: "").normalize()
                        localTitleNorm == targetTitleNorm && (localArtistNorm == targetArtistNorm || localArtistNorm.contains(targetArtistNorm))
                    }

                    if (match != null && !seenIds.contains(match.id)) {
                        trendingSongs.add(match)
                        seenIds.add(match.id)
                    }
                }
                if (trendingSongs.size >= 12) break
            }
        } catch (e: Exception) {
            Log.e("RecManager", "Failed to fetch trending tracks", e)
        }

        // If trending call fails or has no local matches, return a shuffled list of popular (frequent) songs from server
        if (trendingSongs.isEmpty()) {
            try {
                val frequentAlbums = repo.albums("frequent", 5)
                val fallbackTrending = frequentAlbums.flatMap { album ->
                    repo.album(album.id)?.song.orEmpty()
                }.shuffled().take(8)
                trendingSongs.addAll(fallbackTrending)
            } catch (e: Exception) {
                Log.e("RecManager", "Trending fallback failed", e)
            }
        }

        return@withContext trendingSongs
    }
}
