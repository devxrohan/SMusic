<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="120" alt="SMusic logo" />

# SMusic

**Stream, download, and enjoy music — free, ad-free, and yours to customize.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-24-orange.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](#)

</div>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Modules](#modules)
- [Building From Source](#building-from-source)
- [FAQ](#faq)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)

---

> [!NOTE]
> ### 💡 Note Regarding Repository History & Initial Commit
> This repository was made public **after the project had already been fully developed locally**. 
> As a result, the initial public commit contains the complete existing codebase and shows a large number of additions (~136k). 
> All subsequent updates, fixes, and active development are properly tracked through individual commits moving forward.

---

## About

SMusic is a free, open-source Android music app that streams from YouTube Music, with offline downloads, synced lyrics, music recognition, and a deep set of customization options — including **11 distinct in-app player styles** you can switch between, each with its own look, layout, and controls.

You can use it fully signed out — or sign in with your own YouTube account for a personalized Home feed, synced recommendations, and account-scoped search history. No subscription, no ads, ever.


## Features

### Playback
- Stream millions of songs directly from YouTube Music, free and ad-free
- Background playback with full media-notification and lock-screen controls
- Gapless, high-quality audio via Media3 / ExoPlayer
- Home-screen widget for quick playback controls
- Sleep timer with a live countdown
- Shuffle, repeat (all/one), and full queue management
- Radio / similar-song recommendations

### Account & Sync
- Sign in with your own YouTube account (secure in-app WebView login — your credentials never pass through SMusic's own servers)
- Guest mode and signed-in mode are kept fully separate: your search history, recommendations, and Home feed never mix between them
- Account-aware Home feed pulled from your actual YouTube Music activity

### Pulse — Personalized Recommendations
- A ranking engine that learns from what you actually listen to and surfaces it back to you on Home
- Clusters your taste across genres/artists and blends it with YouTube Music's own feed for signed-in users

### AI Studio
- AI-generated playlists built from your own listening history, saved straight to your library as "Recommended by AI"
- AI-assisted lyrics translation
- Bring your own API key (OpenRouter and/or DeepL) in Settings — nothing is sent anywhere without your own key configured

### Audio FX / DJ Studio
- A full in-app DSP studio with dedicated tabs: **Master** (EQ), **Echo & Delay**, and **Turntable** (pitch/speed)
- 8D audio and spatial widening effects, built on custom Media3 audio processors
- Settings persist per-device and apply live during playback

### Voice Assistant
- Hands-free playback: play a song or genre, resume/pause, skip tracks, like the current song, start a radio, and control volume — all by voice

### Android Auto
- Browse your library and control playback from your car's head unit

### Backup & Restore
- Full backup of your library, playlists, and settings to the cloud, with one-tap restore
- Optional automatic background backups

### Offline
- Download individual songs or entire playlists for offline listening
- Downloads survive app updates and are managed from a dedicated storage screen
- Low Data Mode — automatically drops to a lower audio quality on mobile data to save your plan

### Lyrics
- Real-time synced lyrics from **12 providers**: Lrc.red ,LRCLIB, KuGou, BetterLyrics, SimpMusic, Unison, YouLy, Megalobiz, Paxsenix, Portato, plus YouTube's own captions and subtitles — SMusic tries them in order so you almost always get a synced result
- Full-screen lyrics view with smooth line-by-line highlighting
- AI-assisted lyrics translation
- Share a lyric line as a styled image card

### Discovery
- Built-in music recognition — identify any song playing around you
- Import your existing playlists straight from Spotify or YouTube Music
- Explore screen with mood- and genre-based browsing
- Search across songs, videos, albums, and artists, with filters
- Full library management — songs, albums, artists, and playlists, each with their own screen

### Discord Rich Presence
- Shows what you're currently playing on your Discord profile in real time

### Customization
- **11 player styles** to choose from: Classic, Spotify, Apple, Galaxy, Fold, Frost, Paper, Liquid, CloudGlow, Groove, Popsy, and more — each with its own layout
- 3 seek-bar styles per player: Default, Squiggly (animated wave), and Slim
- Classic player dynamically colors itself from each song's own artwork
- Custom theme presets, accent colors, and an SF Pro-based font option
- Customizable, reorderable navigation bar
- Built-in equalizer

### Your Library, Your Stats
- Listening history
- Personal listening insights — your top songs, artists, and albums
- Full backup & restore of your library, playlists, and settings

### Stays Up To Date, On Its Own
- In-app notification bell for announcements
- Automatic background update checks — no need to check manually
- Browse previous versions and their changelogs right from the About screen
- Device-performance-aware animations (automatically lighter on lower-end devices)

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/home.jpg" width="260"/><br/><sub>Home</sub></td>
    <td align="center"><img src="docs/screenshots/now-playing.jpg" width="260"/><br/><sub>Now Playing</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/lyrics.jpg" width="260"/><br/><sub>Lyrics</sub></td>
    <td align="center"><img src="docs/screenshots/search.jpg" width="260"/><br/><sub>Search</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/import-playlists.jpg" width="260"/><br/><sub>Import Playlists</sub></td>
    <td align="center"><img src="docs/screenshots/update-check.jpg" width="260"/><br/><sub>Update Check</sub></td>
  </tr>
</table>

## Tech Stack

- **Kotlin** — 100% Kotlin codebase
- **Jetpack Compose** (Material 3 Expressive) — fully declarative UI, no XML layouts
- **Media3 / ExoPlayer** — playback engine, extended with custom audio processors for the DJ Studio, 8D, and spatial audio effects
- **Room** — local database for library, playlists, and history
- **Hilt** — dependency injection
- **Coil** — image loading and caching
- **WorkManager** — background jobs (Pulse refresh, AI recommendations, update checks)
- **KSP** — annotation processing
- **Custom InnerTube client** — talks directly to YouTube Music's own internal API

## Modules

| Module | Purpose |
|---|---|
| `app` | Main application — UI, playback, database, settings |
| `innertube` | YouTube Music InnerTube API client |
| `kugou` | KuGou lyrics provider |
| `lrclib` | LRCLIB lyrics provider |
| `betterlyrics` | BetterLyrics provider |
| `shazamkit` | Music recognition |
| `jossredconnect` | Auth / backend connector |
| `material-color-utilities` | Google's Material color science library |

## Building From Source

### Requirements

- Android Studio (latest stable)
- JDK 17+
- Android SDK — `compileSdk 36`, `minSdk 24`, `targetSdk 35`

### Setup

1. **Clone the repo:**
   ```bash
   git clone https://github.com/<your-username>/SMusic.git
   ```

2. **Create `local.properties`** in the project root (this file is git-ignored — it never gets pushed):
   ```properties
   sdk.dir=/path/to/your/Android/Sdk

   # Optional — only needed if you're running your own update-checker worker
   update.manifest.url=

   # Used for fetching release changelogs and the GitHub link on the About screen
   github.repo=<your-username>/SMusic

   # Optional — only needed for Discord Rich Presence
   discord.application.id=
   ```

3. **AI Studio (optional)** — to use AI playlist recommendations and lyrics translation, add your own API key(s) from inside the app (Settings → AI), not in `local.properties`. SMusic supports OpenRouter for recommendations and DeepL for translation; nothing AI-related runs until you add a key yourself.

4. **Open the project in Android Studio** and let Gradle sync.

5. **Build and run** on a device or emulator.

To build a *signed* release APK, you'll also need your own `keystore.properties` (also git-ignored) with your signing details:
```properties
storeFile=/path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

## FAQ

**Is this affiliated with YouTube Music or Google?**
No. SMusic is an independent, unofficial client that uses YouTube Music's own public web API — the same one YouTube Music's own website uses.

**Does it cost anything?**
No. SMusic is completely free and always will be — no subscriptions, no premium tier, no ads.

**Do I need to sign in?**
No. Everything works fully signed out. Signing in with your YouTube account just unlocks a personalized Home feed and synced recommendations.

**Does AI Studio cost me anything?**
SMusic itself doesn't charge for it, but AI Studio uses your own OpenRouter/DeepL API key, so usage is billed by those providers according to your own account with them.

**Why isn't this on the Play Store?**
Like most InnerTube-based clients, this app isn't distributed through the Play Store. Download the APK directly from this repo's [Releases](../../releases) page.

## Acknowledgments

SMusic is built on top of the Android and Kotlin open-source ecosystem. Thanks to the teams and maintainers behind:

**Core**
- [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material 3 — UI toolkit
- [Media3 / ExoPlayer](https://developer.android.com/guide/topics/media/media3) — playback engine
- [Room](https://developer.android.com/jetpack/androidx/releases/room) — local database
- [Hilt](https://dagger.dev/hilt/) — dependency injection
- [Coil](https://coil-kt.github.io/coil/) — image loading
- [Kotlin Coroutines & Serialization](https://kotlinlang.org/) — async & JSON handling
- [Ktor Client](https://ktor.io/) — networking
- [Jsoup](https://jsoup.org/) — HTML parsing
- [Timber](https://github.com/JakeWharton/timber) — logging

**UI & interaction**
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) — listening-stats charts
- Haze & Haze Materials, Cloudy, Liquid Glass — glassmorphism / blur effects
- Compose Reorderable, Squiggly Slider, Shimmer, Compose Markdown

**Data & services**
- [LRCLIB](https://lrclib.net/), KuGou, SimpMusic, Unison, YouLy, Megalobiz, Paxsenix, Portato — synced-lyrics data sources
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities) (Google) — dynamic color science, bundled as a module
- [ShazamKit](https://developer.apple.com/shazamkit/) (Apple) — music recognition
- [OpenRouter](https://openrouter.ai/) & [DeepL](https://www.deepl.com/) — power AI Studio when you add your own API key
- Google Identity Services, Credential Manager & Drive API — sign-in and backup
- [Firebase](https://firebase.google.com/) Cloud Messaging — release announcements
- Discord Gateway API — Rich Presence integration

If a library you maintain is used here and isn't credited correctly, please open an issue — it'll be fixed right away.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on reporting issues and submitting pull requests.

## Security

See [SECURITY.md](SECURITY.md) for how to report a vulnerability.

## License

This project is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

> Copyleft: any derivative work built from this project must also be open-sourced under GPL-3.0.

