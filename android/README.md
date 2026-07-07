# Trợ lý nhạc — Android (bit-perfect Navidrome player)

A native Android client for the **Navidrome** server (`ms.troly.me`) aimed at the
same goal as **UAPP**: bit-perfect / hi-res playback to a USB DAC, with a UI that
matches **nhac.troly.me** (the "Trợ lý nhạc" web app). It targets **phones,
tablets, and the X96 Max+ Android TV box** from one codebase.

This is a **scaffold** — the app shell (theme, navigation host, Subsonic client,
Media3 service) is in place; the bit-perfect USB integration and full browse/
now-playing UI are the next steps (marked `TODO`).

## Why this stack
- **Kotlin + Jetpack Compose + Compose for TV (`androidx.tv`)** — one UI for
  touch (phone/tablet) and D-pad/leanback (X96 box).
- **AndroidX Media3 (ExoPlayer)** — playback, gapless, `MediaLibrarySession`
  (drives notification, Android TV, and later Android Auto).
- **decent-player** — the open-source USB Audio Class 2.0 driver that does the
  bit-perfect part (see below). Oboe/AAudio **cannot** do bit-perfect USB output
  (exclusive mode unsupported, stuck at 48 kHz), so the usbdevfs route is the only
  no-root option on Android.
- **Retrofit + kotlinx.serialization** — OpenSubsonic client.

## Theme
Colors mirror the default dark theme of nhac.troly.me (Aonsoku), converted from
its shadcn HSL variables — see `ui/theme/Color.kt`. Background `#070E22`,
emerald primary `#10B77F`.

## UI/UX (adaptive phone + TV)
One Compose UI adapts to the form factor (`LocalIsTv`, detected via leanback):
- **Phone**: bottom `NavigationBar` (Trang chủ / Tìm kiếm / Thư viện), a persistent
  **mini-player**, and a full-screen **Now Playing** sheet.
- **TV (X96 box)**: left `NavigationRail` + **D-pad focus** (cards scale + accent
  border via `Modifier.tvFocusable`), larger targets, mini-player at the bottom.

Structure:
- `ui/AppRoot.kt` — detects TV, builds repository + player, provides them via
  `CompositionLocal`.
- `ui/AppShell.kt` — adaptive navigation + overlays (album detail, now playing).
- `ui/screens/` — `HomeScreen` (Mới thêm / Nghe nhiều / Gần đây / Ngẫu nhiên rows),
  `LibraryScreen` (album grid), `SearchScreen`, `AlbumDetailScreen` (play / shuffle).
- `ui/player/` — `MiniPlayer`, `NowPlayingScreen` (art, seek, transport).
- `ui/components/` — `AlbumCard`/`AlbumRow`, `SongRow`.
- `playback/PlayerConnection.kt` — Media3 `MediaController` → `PlaybackService`,
  builds `MediaItem`s from Subsonic songs (original stream = bit-perfect source).
- `data/subsonic/Subsonic.kt` — auth interceptor + repository (albums, album,
  artists, playlists, search3, starred).

Tapping an album → detail → **Phát** streams from `ms.troly.me` through the bit-
perfect pipeline; the Now Playing sheet shows art + transport + seek.

## Build (Android Studio)
1. Open the `android/` folder in Android Studio (Ladybug+).
2. Let it sync (it will fetch the Gradle wrapper jar and dependencies).
3. Generate launcher icons if you want custom art (an adaptive placeholder icon
   is already included).
4. Run on a device/emulator (min **Android 10 / API 29**).

CLI (after `gradle wrapper` has produced `gradlew`):
```
cd android && ./gradlew :app:assembleDebug
```

## Bit-perfect USB integration (decent-player)
The hard part of UAPP is already open source: <https://github.com/Ma145/decent-player>
(MIT). It ships three Media3-ready modules:
`usb-audio-driver`, `usb-audio-wrapper-media3`, `media3-decoder-flac`.

Steps:
1. Vendor it as a submodule:
   ```
   cd android
   git submodule add https://github.com/Ma145/decent-player decent-player
   ```
2. In `settings.gradle.kts`, `includeBuild("decent-player")` (or include its
   modules directly).
3. In `app/build.gradle.kts`, add the three `implementation(project(...))` deps
   (commented block is already there).
4. Implement `buildAudioSink()` in
   `playback/BitPerfectRenderersFactory.kt` to return decent-player's
   `DecentAudioSink` when a compatible DAC is attached (the exact call is
   documented in that file).
5. Verify with your DAC using the driver's
   `docs/driver/07-verification-and-diagnostics.md`.

**Bit-perfect = no ReplayGain / no volume scaling** in the path. Expose ReplayGain
only as an explicit non-bit-perfect mode (like UAPP).

## Server connection
`data/subsonic/Subsonic.kt` defaults to `https://ms.troly.me` with Subsonic token
auth (`t = md5(password + salt)`). `streamUrl()` requests the **original** file
(no `maxBitRate`/`format`) so Navidrome serves lossless — the bit-perfect source.

## Caveats
- **X96 Max+ (S905X3)** often ships **Android 9**; the driver needs **Android
  10+**. Check the box's version — you may need an Android 10+/ATV ROM, else
  bit-perfect USB won't work there (phones/tablets on 10+ are fine).
- decent-player is early (v0.1.0) with limited DAC coverage — test yours.
- **DSD** is on the driver's roadmap, not implemented yet.

## Roadmap
1. Server login + library browse (albums/artists/playlists) via Subsonic.
2. Now-playing + queue on Media3; gapless.
3. Wire decent-player AudioSink → bit-perfect USB; DAC verification.
4. Compose-for-TV surface for the X96 box.
5. Offline cache, ReplayGain (optional mode), Android Auto.
6. DSD (DoP/native) once the driver supports it; IEM EQ presets.
