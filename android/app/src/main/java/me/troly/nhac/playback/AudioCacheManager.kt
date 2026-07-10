package me.troly.nhac.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Audiophile Caching & Prefetch Engine using Jetpack Media3 SimpleCache.
 * Caps disk cache to 512MB using LeastRecentlyUsed (LRU) eviction to keep storage lean.
 *
 * Provides custom CacheDataSource.Factory to route all stream requests through cache automatically,
 * and allows pre-buffering (prefetching) the first 1MB of the subsequent track.
 */
@UnstableApi
object AudioCacheManager {
    private const val CACHE_DIR_NAME = "audiophile_track_cache"
    private const val MAX_CACHE_SIZE_BYTES = 512 * 1024 * 1024L // 512 MB

    private var cache: SimpleCache? = null
    private var prefetchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            cache = SimpleCache(cacheDir, evictor)
            Log.d("AudioCacheManager", "SimpleCache initialized at ${cacheDir.absolutePath} with max size of 512MB")
        }
        return cache!!
    }

    /**
     * Builds a CacheDataSource factory.
     * With DefaultHttpDataSource as the upstream source, it automatically populates the SimpleCache
     * during playback. Subsequent replays are instantly read from disk with zero network utilization.
     */
    fun getCacheDataSourceFactory(context: Context): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Asynchronously pre-caches the first 1MB of a track's audio stream.
     * 1MB of FLAC/VBR represents 10-20 seconds of audio. Downloading this segment beforehand
     * ensures that when the track is played next, ExoPlayer initiates playback from local storage instantly
     * with 0ms gap, completely bypassing initial network handshakes and server transcoding spin-up delays.
     */
    fun prefetch(context: Context, url: String) {
        val uri = Uri.parse(url)
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            try {
                Log.d("AudioCacheManager", "Prefetch started for: $url")
                // Fetch first 1MB (1,048,576 bytes) of the file
                val dataSpec = DataSpec(uri, 0, 1024 * 1024L)
                val cacheDataSource = CacheDataSource.Factory()
                    .setCache(getCache(context))
                    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
                    .createDataSource()

                CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    null, // progress listener
                    null  // cancel listener
                ).cache()
                Log.d("AudioCacheManager", "Prefetch completed (1MB buffered) for: $url")
            } catch (e: Exception) {
                Log.e("AudioCacheManager", "Prefetch failed for $url", e)
            }
        }
    }
}
