package me.troly.nhac

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.decent.usbaudio.UsbAudioPermissionHelper
import me.troly.nhac.data.SettingsStore
import me.troly.nhac.data.subsonic.ServerConfig
import me.troly.nhac.ui.AppRoot
import me.troly.nhac.ui.LoginScreen
import me.troly.nhac.ui.theme.TroLyNhacTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Claim a USB DAC if we were launched by USB_DEVICE_ATTACHED.
        UsbAudioPermissionHelper.handleIntent(applicationContext, intent)

        enableEdgeToEdge()
        val settings = SettingsStore(applicationContext)

        setContent {
            TroLyNhacTheme {
                var config by remember { mutableStateOf<ServerConfig?>(settings.load()) }
                val current = config
                if (current == null) {
                    LoginScreen(onConnected = {
                        settings.save(it)
                        config = it
                    })
                } else {
                    AppRoot(config = current)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A DAC was attached while the app was already running.
        UsbAudioPermissionHelper.handleIntent(applicationContext, intent)
    }
}
