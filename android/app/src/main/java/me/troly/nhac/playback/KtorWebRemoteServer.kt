package me.troly.nhac.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


/**
 * Ktor-backed Web Remote Server that runs inside the PlaybackService on the TV Box.
 * It provides a REST API to control playback and advertises itself over the local network via mDNS.
 */
class KtorWebRemoteServer(
    private val context: Context,
    private val player: Player
) {
    private val tag = "KtorWebRemoteServer"
    private var server: NettyApplicationEngine? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serverPort = 8080
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    private fun findAvailablePort(startPort: Int): Int {
        var port = startPort
        while (port < startPort + 10) {
            try {
                java.net.ServerSocket(port).use {
                    return port
                }
            } catch (e: Exception) {
                port++
            }
        }
        return startPort
    }


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    data class PlayerStatus(
        val isPlaying: Boolean,
        val currentPositionMs: Long,
        val durationMs: Long,
        val volume: Float,
        val title: String?,
        val artist: String?
    )

    @Serializable
    data class SimpleResponse(val success: Boolean, val message: String)

    @Serializable
    data class PlayTrackRequest(
        val songId: String,
        val streamUrl: String,
        val title: String,
        val artist: String
    )

    @Serializable
    data class DiscoverResponse(
        val type: String,
        val deviceName: String,
        val version: String
    )


    fun start() {
        // Tìm cổng rảnh trước để tránh xung đột cổng
        serverPort = findAvailablePort(8080)
        Log.i(tag, "Starting Ktor Web Remote Server on selected port $serverPort...")
        
        // Kích hoạt Multicast Lock để mDNS luôn phát sóng ổn định kể cả khi TVBox sleep/idle
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            multicastLock = wifiManager?.createMulticastLock("trolynhac_mdns_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.i(tag, "Wifi Multicast Lock acquired successfully.")
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire Wifi Multicast Lock: ${e.message}")
        }

        // 1. Start mDNS advertising
        registerMdnsService()

        // 2. Start Ktor Netty Server
        scope.launch {
            try {
                server = embeddedServer(Netty, port = serverPort) {
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    }
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader(HttpHeaders.Authorization)
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                        allowMethod(HttpMethod.Options)
                    }
                    routing {
                        get("/") {
                            call.respond(SimpleResponse(true, "Chào mừng tới Trợ lý nhạc Volumio-like Headless Player!"))
                        }

                        get("/api/discover") {
                            call.respond(DiscoverResponse(
                                type = "trolynhac_renderer",
                                deviceName = android.os.Build.MODEL ?: "X96 M300",
                                version = "0.1.0"
                            ))
                        }
                        
                        get("/api/status") {
                            // Run on Main dispatcher because Player methods must be accessed on the main thread
                            val status = kotlinx.coroutines.withContext(Dispatchers.Main) {
                                val currentMedia = player.currentMediaItem
                                PlayerStatus(
                                    isPlaying = player.isPlaying,
                                    currentPositionMs = player.currentPosition,
                                    durationMs = player.duration.let { if (it < 0) 0 else it },
                                    volume = player.volume,
                                    title = currentMedia?.mediaMetadata?.title?.toString(),
                                    artist = currentMedia?.mediaMetadata?.artist?.toString()
                                )
                            }
                            call.respond(status)
                        }

                        post("/api/play") {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                player.play()
                            }
                            call.respond(SimpleResponse(true, "Playback started"))
                        }

                        post("/api/pause") {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                player.pause()
                            }
                            call.respond(SimpleResponse(true, "Playback paused"))
                        }

                        post("/api/next") {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                player.seekToNext()
                            }
                            call.respond(SimpleResponse(true, "Skipped to next track"))
                        }

                        post("/api/previous") {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                player.seekToPrevious()
                            }
                            call.respond(SimpleResponse(true, "Skipped to previous track"))
                        }

                        post("/api/play-track") {
                            val request = call.receive<PlayTrackRequest>()
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setUri(request.streamUrl)
                                    .setMediaId(request.songId)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(request.title)
                                            .setArtist(request.artist)
                                            .build()
                                    )
                                    .build()
                                player.setMediaItem(mediaItem)
                                player.prepare()
                                player.play()
                            }
                            call.respond(SimpleResponse(true, "Track loaded successfully"))
                        }
                    }
                }.start(wait = false)
                Log.i(tag, "Ktor Web Remote Server successfully launched.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start Ktor Web Remote Server: ${e.message}", e)
            }
        }
    }

    fun stop() {
        Log.i(tag, "Stopping Ktor Web Remote Server...")
        try {
            server?.stop(1000, 2000)
            unregisterMdnsService()
            
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.i(tag, "Wifi Multicast Lock released.")
            }
            
            Log.i(tag, "Ktor Web Remote Server stopped.")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping server: ${e.message}", e)
        }
    }

    private fun registerMdnsService() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "vi2play"
                serviceType = "_http._tcp."
                port = serverPort
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.i(tag, "mDNS Service successfully registered as: ${NsdServiceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(tag, "mDNS Service registration failed with error code: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i(tag, "mDNS Service successfully unregistered.")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(tag, "mDNS Service unregistration failed with error code: $errorCode")
                }
            }

            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to register mDNS service: ${e.message}", e)
        }
    }

    private fun unregisterMdnsService() {
        try {
            registrationListener?.let {
                nsdManager?.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering mDNS service: ${e.message}", e)
        }
    }
}
