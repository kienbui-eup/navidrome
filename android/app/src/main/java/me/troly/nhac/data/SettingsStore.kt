package me.troly.nhac.data

import android.content.Context
import me.troly.nhac.data.subsonic.ServerConfig

/**
 * Tiny SharedPreferences-backed store for the server connection.
 * NOTE: password is stored in plaintext for now (personal app). Swap in
 * EncryptedSharedPreferences before shipping widely.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("trolynhac", Context.MODE_PRIVATE)

    fun load(): ServerConfig? {
        val url = prefs.getString("baseUrl", null) ?: return null
        val user = prefs.getString("username", "") ?: ""
        if (url.isBlank() || user.isBlank()) return null
        return ServerConfig(
            baseUrl = url,
            username = user,
            password = prefs.getString("password", "") ?: "",
        )
    }

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString("baseUrl", config.baseUrl)
            .putString("username", config.username)
            .putString("password", config.password)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
