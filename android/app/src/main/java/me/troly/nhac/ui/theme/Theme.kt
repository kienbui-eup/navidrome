package me.troly.nhac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is dark-first to match nhac.troly.me. A light scheme can be added later;
// for now both modes resolve to the dark palette.
private val NhacDarkColors = darkColorScheme(
    primary = NhacPrimary,
    onPrimary = NhacOnPrimary,
    secondary = NhacSecondary,
    onSecondary = NhacOnBackground,
    background = NhacBackground,
    onBackground = NhacOnBackground,
    surface = NhacSurface,
    onSurface = NhacOnBackground,
    surfaceVariant = NhacSurfaceVariant,
    onSurfaceVariant = NhacOnSurfaceVariant,
    outline = NhacOutline,
    error = NhacError,
    onError = NhacOnError,
)

@Composable
fun TroLyNhacTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NhacDarkColors,
        typography = NhacTypography,
        content = content,
    )
}
