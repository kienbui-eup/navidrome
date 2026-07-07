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
            _state.value = PlaybackState(
                current = player.mediaMetadata,
                isPlaying = player.isPlaying,
                hasMedia = player.mediaItemCount > 0,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.coerceAtLeast(0),
            )
        }
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
        _state.value = _state.value.copy(
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
        )
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
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(config.streamUrl(id)) // original file (bit-perfect source)
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
