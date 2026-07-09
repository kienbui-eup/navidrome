package me.troly.nhac.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.troly.nhac.data.subsonic.ServerConfig
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.playback.PlayerConnection
import me.troly.nhac.playback.RecommendationManager
import me.troly.nhac.ui.tv.TvApp

/**
 * Top-level composable once a server is configured. Detects the form factor
 * (phone vs Android TV / X96 box), wires the repository + player, and hosts the
 * adaptive [AppShell].
 */
@Composable
fun AppRoot(config: ServerConfig) {
    val context = LocalContext.current
    val isTv = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature("android.hardware.type.television")
    }
    val repo = remember(config) { SubsonicRepository(config) }
    val player = remember(config) { PlayerConnection(context.applicationContext, config) }
    val recManager = remember { RecommendationManager(context.applicationContext) }

    DisposableEffect(player) { onDispose { player.release() } }

    ProvideAppEnv(isTv = isTv, player = player, repo = repo, recManager = recManager) {
        if (isTv) TvApp() else AppShell()
    }
}
