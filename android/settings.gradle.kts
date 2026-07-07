pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jellyfin-built FFmpeg decoder for Media3 (ALAC/WavPack/Opus/… → float PCM)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TroLyNhac"
include(":app")

// ── Bit-perfect USB Audio (decent-player) ──────────────────────────────────
// Vendored (plain clone, gitignored) under ./decent-player. Include its three
// modules by path so the app can depend on them directly.
include(":decent-usb-audio-driver")
project(":decent-usb-audio-driver").projectDir =
    file("decent-player/libs/decent-usb-audio-driver")
include(":decent-usb-audio-wrapper-media3")
project(":decent-usb-audio-wrapper-media3").projectDir =
    file("decent-player/libs/decent-usb-audio-wrapper-media3")
include(":decent-media3-decoder-flac")
project(":decent-media3-decoder-flac").projectDir =
    file("decent-player/libs/decent-media3-decoder-flac")
