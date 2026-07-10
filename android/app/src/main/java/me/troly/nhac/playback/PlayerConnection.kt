package me.troly.nhac.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.ServerConfig
import me.troly.nhac.data.subsonic.Song
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix
import me.troly.nhac.data.subsonic.streamUrl

/** Snapshot of what's playing, for the UI. */
data class PlaybackState(
    val current: MediaMetadata? = null,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Bridges the UI to [PlaybackService] via a Media3 [MediaController], exposing a
 * simple [state] flow and playback commands. One instance lives for the app.
 */
class PlayerConnection(context: Context, private val config: ServerConfig) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val recManager = RecommendationManager(context)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Persistent DSP & Filter configurations
    private val dspPrefs = context.getSharedPreferences("trolynhac_dsp", Context.MODE_PRIVATE)

    private val _activeFilter = MutableStateFlow(dspPrefs.getString("activeFilter", "Bypass") ?: "Bypass")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    private val _activeDither = MutableStateFlow(dspPrefs.getString("activeDither", "None") ?: "None")
    val activeDither: StateFlow<String> = _activeDither.asStateFlow()

    private val _activeModulator = MutableStateFlow(dspPrefs.getString("activeModulator", "PCM (Bit-Perfect)") ?: "PCM (Bit-Perfect)")
    val activeModulator: StateFlow<String> = _activeModulator.asStateFlow()

    fun setFilter(filter: String) {
        dspPrefs.edit().putString("activeFilter", filter).apply()
        _activeFilter.value = filter
    }

    fun setDither(dither: String) {
        dspPrefs.edit().putString("activeDither", dither).apply()
        _activeDither.value = dither
    }

    fun setModulator(modulator: String) {
        dspPrefs.edit().putString("activeModulator", modulator).apply()
        _activeModulator.value = modulator
    }

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = snapshot(player)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                checkAndTriggerAutoRadio(player)
            }
        }
    }

    /**
     * Builds a [PlaybackState] from the player, choosing a reliable duration.
     *
     * DSD (.dsf/.dff) is transcoded to FLAC on the fly by the server, so the stream
     * carries no total length (STREAMINFO total_samples = 0). ExoPlayer then estimates
     * [Player.getDuration] from Content-Length/bitrate — a VBR FLAC guess that drifts and
     * makes the seek bar jump. The Subsonic metadata already has the true duration, so we
     * trust it for transcoded items. [Player.getCurrentPosition] stays accurate either way.
     */
    private fun snapshot(player: Player): PlaybackState {
        val extras = player.currentMediaItem?.mediaMetadata?.extras
        val knownMs = extras?.getLong("durationMs") ?: 0L
        val suffix = extras?.getString("suffix")
        val exoMs = player.duration // may be C.TIME_UNSET (negative)
        val durationMs = when {
            isServerTranscodeSuffix(suffix) && knownMs > 0 -> knownMs
            exoMs > 0 -> exoMs
            else -> knownMs
        }
        return PlaybackState(
            current = player.mediaMetadata,
            isPlaying = player.isPlaying,
            hasMedia = player.mediaItemCount > 0,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
        )
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
        }, MoreExecutors.directExecutor())
    }

    /** Refresh position for the seek bar (call on a ticker while playing). */
    fun tick() {
        val c = controller ?: return
        _state.value = snapshot(c)
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0)
        c.prepare()
        c.play()
    }

    fun appendSongs(songs: List<Song>) {
        val c = controller ?: return
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun appendSong(song: Song) {
        val c = controller ?: return
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun getQueue(): List<MediaMetadata> {
        val c = controller ?: return emptyList()
        val list = mutableListOf<MediaMetadata>()
        for (i in 0 until c.mediaItemCount) {
            list.add(c.getMediaItemAt(i).mediaMetadata)
        }
        return list
    }

    fun getCurrentIndex(): Int {
        return controller?.currentMediaItemIndex ?: -1
    }

    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        try {
            scope.launch { /* cancel scope helper */ }
            scope.coroutineContext.get(kotlinx.coroutines.Job)?.cancel()
        } catch (e: Exception) {}
    }

    private fun Song.toMediaItem(): MediaItem {
        val extras = android.os.Bundle().apply {
            putString("songId", id)
            putString("suffix", suffix)
            putInt("bitRate", bitRate ?: 0)
            putInt("bitDepth", bitDepth ?: 0)
            putInt("samplingRate", samplingRate ?: 0)
            putString("artistId", artistId)
            putString("albumId", albumId)
            putString("coverArt", coverArt)
            putString("starred", starred)
            putInt("userRating", userRating ?: 0)
            // Server-reported duration (seconds → ms); authoritative for transcoded DSD.
            putLong("durationMs", (duration ?: 0).toLong() * 1000L)
        }
        // DSD (.dsf/.dff) can't be demuxed by ExoPlayer nor output raw by Android;
        // ask the server to transcode it to FLAC. Everything else streams Original
        // (bit-perfect). See isServerTranscodeSuffix / streamUrl.
        val format = if (isServerTranscodeSuffix(suffix)) "flac" else null
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(config.streamUrl(id, format))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(config.coverArtUrl(coverArt, 600)?.let(android.net.Uri::parse))
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private var isFetchingAutoRadio = false

    private fun checkAndTriggerAutoRadio(player: Player) {
        val currentItem = player.currentMediaItem ?: return
        val currentIndex = player.currentMediaItemIndex
        val totalItems = player.mediaItemCount
        
        // Trigger when we transition to the last song in the queue and auto-radio is enabled
        if (recManager.autoRadioEnabled.value && currentIndex == totalItems - 1 && !isFetchingAutoRadio) {
            val title = currentItem.mediaMetadata.title?.toString() ?: ""
            val artist = currentItem.mediaMetadata.artist?.toString() ?: ""
            val extras = currentItem.mediaMetadata.extras
            val artistId = extras?.getString("artistId")
            
            if (title.isNotBlank()) {
                isFetchingAutoRadio = true
                scope.launch {
                    try {
                        Log.d("PlayerConn", "Auto-Radio: Fetching recommendations for $title - $artist")
                        val repo = SubsonicRepository(config)
                        val recommendations = recManager.recommendSimilar(
                            currentSongTitle = title,
                            currentArtistName = artist,
                            currentArtistId = artistId,
                            repo = repo
                        )
                        if (recommendations.isNotEmpty()) {
                            val queueIds = getQueueMediaIds(player)
                            val newSongs = recommendations.filter { !queueIds.contains(it.id) }
                            
                            if (newSongs.isNotEmpty()) {
                                Log.d("PlayerConn", "Auto-Radio: Appending ${newSongs.size} new songs to the queue")
                                player.addMediaItems(newSongs.map { it.toMediaItem() })
                            } else {
                                Log.d("PlayerConn", "Auto-Radio: All recommendations are already in the queue")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerConn", "Auto-Radio failure", e)
                    } finally {
                        isFetchingAutoRadio = false
                    }
                }
            }
        }
    }

    private fun getQueueMediaIds(player: Player): Set<String> {
        val ids = mutableSetOf<String>()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            ids.add(item.mediaId)
        }
        return ids
    }
}
