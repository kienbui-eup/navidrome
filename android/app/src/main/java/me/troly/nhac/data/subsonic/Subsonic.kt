package me.troly.nhac.data.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Body
import retrofit2.http.Path
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.flow.asSharedFlow

/** Connection settings for the Navidrome (Subsonic/OpenSubsonic) server. */
data class ServerConfig(
    val baseUrl: String = "https://ms.troly.me",
    val username: String = "",
    val password: String = "",
    val clientName: String = "TroLyNhac",
    val apiVersion: String = "1.16.1",
)

object SubsonicAuth {
    fun salt(): String = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
    fun token(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }
}

/** Appends Subsonic auth (u,t,s,v,c,f) to every request — a fresh salt per call. */
class AuthInterceptor(private val config: ServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val salt = SubsonicAuth.salt()
        val url: HttpUrl = chain.request().url.newBuilder()
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", SubsonicAuth.token(config.password, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", config.apiVersion)
            .addQueryParameter("c", config.clientName)
            .addQueryParameter("f", "json")
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}

// Original-quality stream (no maxBitRate/format) — the bit-perfect source.
private fun ServerConfig.authQuery(): String {
    val salt = SubsonicAuth.salt()
    return "u=$username&t=${SubsonicAuth.token(password, salt)}&s=$salt&v=$apiVersion&c=$clientName"
}
// With no [format], the server sends the Original file (bit-perfect PCM/FLAC).
// Pass format="flac" for sources Android/ExoPlayer can't demux natively (DSD:
// .dsf/.dff) so the server transcodes DSD → 24-bit FLAC PCM on the fly.
fun ServerConfig.streamUrl(id: String, format: String? = null): String {
    val url = "${baseUrl.trimEnd('/')}/rest/stream.view?id=$id&${authQuery()}"
    return if (format.isNullOrBlank()) url else "$url&format=$format"
}

/** Codecs ExoPlayer/Media3 has no extractor for → must be server-transcoded. */
fun isServerTranscodeSuffix(suffix: String?): Boolean =
    suffix?.lowercase() in setOf("dsf", "dff", "dsd", "aif", "aiff")
fun ServerConfig.coverArtUrl(coverArtId: String?, size: Int = 300): String? =
    coverArtId?.let { "${baseUrl.trimEnd('/')}/rest/getCoverArt.view?id=$it&size=$size&${authQuery()}" }

// ── API ─────────────────────────────────────────────────────────────────────
interface SubsonicApi {
    @GET("rest/ping.view") suspend fun ping(): SubsonicResponse

    @GET("rest/getAlbumList2.view")
    suspend fun albumList(@Query("type") type: String, @Query("size") size: Int = 40,
                          @Query("offset") offset: Int = 0): SubsonicResponse

    @GET("rest/getAlbum.view") suspend fun album(@Query("id") id: String): SubsonicResponse
    @GET("rest/getArtist.view") suspend fun artist(@Query("id") id: String): SubsonicResponse
    @GET("rest/getArtists.view") suspend fun artists(): SubsonicResponse
    @GET("rest/getPlaylists.view") suspend fun playlists(): SubsonicResponse
    @GET("rest/getPlaylist.view") suspend fun playlist(@Query("id") id: String): SubsonicResponse
    @GET("rest/getStarred2.view") suspend fun starred(): SubsonicResponse

    @GET("rest/search3.view")
    suspend fun search(@Query("query") query: String, @Query("songCount") songCount: Int = 30,
                       @Query("albumCount") albumCount: Int = 20,
                       @Query("artistCount") artistCount: Int = 20): SubsonicResponse

    // Each list element becomes a repeated query param (songId=a&songId=b…).
    @GET("rest/createPlaylist.view")
    suspend fun createPlaylist(@Query("name") name: String,
                               @Query("songId") songId: List<String>): SubsonicResponse

    @GET("rest/updatePlaylist.view")
    suspend fun updatePlaylist(@Query("playlistId") playlistId: String,
                               @Query("songIdToAdd") songIdToAdd: List<String>): SubsonicResponse

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicResponse

    @GET("rest/getScanStatus.view")
    suspend fun getScanStatus(): SubsonicResponse

    @GET("rest/getSong.view")
    suspend fun song(@Query("id") id: String): SubsonicResponse

    @GET("rest/star.view")
    suspend fun star(@Query("id") id: String): SubsonicResponse

    @GET("rest/unstar.view")
    suspend fun unstar(@Query("id") id: String): SubsonicResponse

    @GET("rest/setRating.view")
    suspend fun setRating(@Query("id") id: String, @Query("rating") rating: Int): SubsonicResponse
}

// ── NATIVE API ──────────────────────────────────────────────────────────────
@Serializable
data class SsoRequest(
    val username: String,
    val token: String,
    val salt: String
)

@Serializable
data class SsoResponse(
    val id: String,
    val name: String,
    val username: String,
    val isAdmin: Boolean,
    val token: String
)

@Serializable
data class ScanResponse(
    val status: String
)

@Serializable
data class DeleteResponse(
    val ids: List<String>
)

@Serializable
data class AudiophileResponse(
    @SerialName("media_file_id") val mediaFileId: String = "",
    @SerialName("real_lossless") val realLossless: Boolean = true,
    @SerialName("cutoff_frequency") val cutoffFrequency: Double = 22000.0,
    @SerialName("dynamic_range_score") val dynamicRangeScore: Int = 10,
    @SerialName("analyzed_at") val analyzedAt: String = "",
    val status: String? = null
)

interface NativeApi {
    @POST("auth/sso/subsonic")
    suspend fun ssoLogin(@Body body: SsoRequest): SsoResponse

    @POST("api/import/scan")
    suspend fun triggerScan(@Header("X-VI-Authorization") authHeader: String): ScanResponse

    @DELETE("api/song/{id}")
    suspend fun deleteSong(
        @Header("X-VI-Authorization") authHeader: String,
        @Path("id") id: String
    ): DeleteResponse

    @DELETE("api/album/{id}")
    suspend fun deleteAlbum(
        @Header("X-VI-Authorization") authHeader: String,
        @Path("id") id: String
    ): DeleteResponse

    @GET("api/song/{id}/audiophile")
    suspend fun getSongAudiophile(
        @Header("X-VI-Authorization") authHeader: String,
        @Path("id") id: String
    ): AudiophileResponse
}



// ── DTOs ────────────────────────────────────────────────────────────────────
@Serializable data class SubsonicResponse(@SerialName("subsonic-response") val response: SubsonicBody? = null)

@Serializable data class SubsonicBody(
    val status: String = "",
    val version: String = "",
    val albumList2: AlbumList2? = null,
    val album: Album? = null,
    val artist: Artist? = null,
    val artists: ArtistsRoot? = null,
    val playlists: PlaylistsRoot? = null,
    val playlist: Playlist? = null,
    val starred2: Starred2? = null,
    val searchResult3: SearchResult3? = null,
    val scanStatus: ScanStatus? = null,
    val song: Song? = null,
    val error: SubsonicError? = null,
)

@Serializable data class SubsonicError(val code: Int = 0, val message: String = "")
@Serializable data class AlbumList2(val album: List<Album> = emptyList())
@Serializable data class ArtistsRoot(val index: List<ArtistIndex> = emptyList())
@Serializable data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())
@Serializable data class PlaylistsRoot(val playlist: List<Playlist> = emptyList())
@Serializable data class Starred2(val album: List<Album> = emptyList(), val song: List<Song> = emptyList())
@Serializable data class ScanStatus(val scanning: Boolean = false, val count: Int? = null)
@Serializable data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable data class Album(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val year: Int? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val song: List<Song> = emptyList(), // populated by getAlbum
)

@Serializable data class Artist(
    val id: String, val name: String,
    val coverArt: String? = null, val albumCount: Int? = null,
    val album: List<Album> = emptyList(), // populated by getArtist
)

@Serializable data class Playlist(
    val id: String, val name: String,
    val coverArt: String? = null, val songCount: Int? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable data class Song(
    val id: String,
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val samplingRate: Int? = null,
    val starred: String? = null,
    val userRating: Int? = null,
)

/** Domain-facing repository: builds the client and returns parsed lists/objects. */
class SubsonicRepository(val config: ServerConfig) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    private val _refreshEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()

    fun triggerLocalRefresh() {
        _refreshEvent.tryEmit(Unit)
    }

    private val api: SubsonicApi by lazy {
        val http = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(config))
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        Retrofit.Builder()
            .baseUrl(config.baseUrl.trimEnd('/') + "/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
    }

    private val nativeApi: NativeApi by lazy {
        val http = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        Retrofit.Builder()
            .baseUrl(config.baseUrl.trimEnd('/') + "/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NativeApi::class.java)
    }

    private var cachedSsoToken: String? = null
    var isAdmin: Boolean? = null
        private set

    suspend fun getSsoToken(): String {
        cachedSsoToken?.let { return it }
        val salt = SubsonicAuth.salt()
        val token = SubsonicAuth.token(config.password, salt)
        val response = nativeApi.ssoLogin(SsoRequest(config.username, token, salt))
        isAdmin = response.isAdmin
        cachedSsoToken = response.token
        return response.token
    }

    suspend fun checkAdminStatus(): Boolean {
        if (isAdmin != null) return isAdmin!!
        return try {
            getSsoToken()
            isAdmin ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun triggerLibraryScan(): Boolean {
        val sso = getSsoToken()
        val res = nativeApi.triggerScan("Bearer $sso")
        val success = res.status == "scan_started"
        if (success) {
            triggerLocalRefresh()
        }
        return success
    }

    suspend fun deleteSong(songId: String): Boolean {
        val sso = getSsoToken()
        val res = nativeApi.deleteSong("Bearer $sso", songId)
        val success = res.ids.contains(songId)
        if (success) {
            triggerLocalRefresh()
        }
        return success
    }

    suspend fun deleteAlbum(albumId: String): Boolean {
        val sso = getSsoToken()
        val res = nativeApi.deleteAlbum("Bearer $sso", albumId)
        val success = res.ids.contains(albumId)
        if (success) {
            triggerLocalRefresh()
        }
        return success
    }

    private fun body(r: SubsonicResponse): SubsonicBody {
        val b = r.response ?: throw IllegalStateException("Phản hồi rỗng")
        if (b.status != "ok") throw IllegalStateException(b.error?.message ?: "Máy chủ trả lỗi")
        return b
    }

    suspend fun getSongAudiophile(songId: String): AudiophileResponse {
        val sso = getSsoToken()
        return nativeApi.getSongAudiophile("Bearer $sso", songId)
    }

    suspend fun ping(): Boolean = api.ping().response?.status == "ok"
    suspend fun albums(type: String, size: Int = 40) = body(api.albumList(type, size)).albumList2?.album ?: emptyList()
    suspend fun album(id: String) = body(api.album(id)).album
    suspend fun artist(id: String) = body(api.artist(id)).artist
    suspend fun artists() = body(api.artists()).artists?.index?.flatMap { it.artist } ?: emptyList()
    suspend fun playlists() = body(api.playlists()).playlists?.playlist ?: emptyList()
    suspend fun playlist(id: String) = body(api.playlist(id)).playlist
    suspend fun starredSongs() = body(api.starred()).starred2?.song ?: emptyList()
    suspend fun search(q: String) = body(api.search(q)).searchResult3 ?: SearchResult3()
    suspend fun getScanStatus() = body(api.getScanStatus()).scanStatus ?: ScanStatus()
    suspend fun song(id: String) = body(api.song(id)).song ?: throw IllegalStateException("Không tìm thấy bài hát")

    suspend fun star(id: String) {
        body(api.star(id))
        triggerLocalRefresh()
    }

    suspend fun unstar(id: String) {
        body(api.unstar(id))
        triggerLocalRefresh()
    }

    suspend fun setRating(id: String, rating: Int) {
        body(api.setRating(id, rating))
        triggerLocalRefresh()
    }

    /** Creates a new playlist seeded with [songIds]. */
    suspend fun createPlaylist(name: String, songIds: List<String>) {
        body(api.createPlaylist(name, songIds))
        triggerLocalRefresh()
    }

    /** Appends [songIds] to an existing playlist. */
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        body(api.updatePlaylist(playlistId, songIds))
        triggerLocalRefresh()
    }

    /** Deletes a playlist (must be owned by the current user). */
    suspend fun deletePlaylist(id: String) {
        body(api.deletePlaylist(id))
        triggerLocalRefresh()
    }
}
