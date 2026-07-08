package me.troly.nhac.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.troly.nhac.data.subsonic.ServerConfig
import me.troly.nhac.data.subsonic.Song
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

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = snapshot(player)
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

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem {
        val extras = android.os.Bundle().apply {
            putString("suffix", suffix)
            putInt("bitRate", bitRate ?: 0)
            putInt("bitDepth", bitDepth ?: 0)
            putInt("samplingRate", samplingRate ?: 0)
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
}
