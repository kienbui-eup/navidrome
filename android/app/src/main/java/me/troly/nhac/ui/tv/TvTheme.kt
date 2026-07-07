package me.troly.nhac.ui.tv

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import me.troly.nhac.ui.theme.NhacBackground
import me.troly.nhac.ui.theme.NhacOnBackground
import me.troly.nhac.ui.theme.NhacOnPrimary
import me.troly.nhac.ui.theme.NhacOnSurfaceVariant
import me.troly.nhac.ui.theme.NhacPrimary
import me.troly.nhac.ui.theme.NhacSurfaceVariant

/** androidx.tv.material3 theme sharing the nhac.troly.me palette (10-foot UI). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NhacPrimary,
            onPrimary = NhacOnPrimary,
            background = NhacBackground,
            onBackground = NhacOnBackground,
            surface = NhacBackground,
            onSurface = NhacOnBackground,
            surfaceVariant = NhacSurfaceVariant,
            onSurfaceVariant = NhacOnSurfaceVariant,
            border = NhacSurfaceVariant,
        ),
        content = content,
    )
}
