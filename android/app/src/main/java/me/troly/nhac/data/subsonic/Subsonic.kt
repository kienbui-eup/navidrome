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
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random

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
fun ServerConfig.streamUrl(id: String): String =
    "${baseUrl.trimEnd('/')}/rest/stream.view?id=$id&${authQuery()}"
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
    val error: SubsonicError? = null,
)

@Serializable data class SubsonicError(val code: Int = 0, val message: String = "")
@Serializable data class AlbumList2(val album: List<Album> = emptyList())
@Serializable data class ArtistsRoot(val index: List<ArtistIndex> = emptyList())
@Serializable data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())
@Serializable data class PlaylistsRoot(val playlist: List<Playlist> = emptyList())
@Serializable data class Starred2(val album: List<Album> = emptyList(), val song: List<Song> = emptyList())
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
)

/** Domain-facing repository: builds the client and returns parsed lists/objects. */
class SubsonicRepository(val config: ServerConfig) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
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

    private fun body(r: SubsonicResponse): SubsonicBody {
        val b = r.response ?: throw IllegalStateException("Phản hồi rỗng")
        if (b.status != "ok") throw IllegalStateException(b.error?.message ?: "Máy chủ trả lỗi")
        return b
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
}
