# Yumi Co. Radio for Android

The official native Android app for
[Yumi Co. Radio](https://yumicoradio.net), a 24/7 online radio station playing
Future Funk, City Pop, Nu Disco and Vaporwave, with a retro Windows 9x look.

No ads, no accounts, no analytics and no third-party tracking.

## Download

[Get Yumi Co. Radio on F-Droid](https://f-droid.org/packages/net.yumicoradio.android/)

## Features

**Player**

- Live stream at 256 kbps, 128 kbps (MP3) or 64 kbps (AAC).
- Background and lock-screen playback, with notification, Bluetooth and headset
  controls.
- Android Auto.
- Now playing information with artist, title, cover art, elapsed time and bitrate.
- A real VU meter measured from the decoded audio. No microphone permission is
  required.
- Ten-band equalizer with twenty-two presets.
- Sleep timer and share.

**Live chat**

- The station's chat: channels, private messages, emotes, file uploads with a
  quota, and presence (online / away / busy).
- Tappable links, platform badges, and inline emote and mention autocomplete.
- Custom nickname colours and optional per-message timestamps.
- Optional background connection. Notifications can be set to every message,
  mentions and PMs only, or none.
- Reserved nicknames are supported. The password is asked once per launch; you
  can opt in to remembering it, stored encrypted on the device via the Android
  Keystore, and turn that off again from the chat options.

**Schedule and history**

- The current hour as a live bar, what is on now, and what is coming up.
- Recently played, with thumbnails.

**Appearance**

- Light and dark modes based on the website's own themes.

## Tech

Kotlin · Jetpack Compose (Material3 restyled with Win9x tokens) · AndroidX Media3
(ExoPlayer + `MediaLibraryService`) · OkHttp · kotlinx.serialization · Coil ·
DataStore · Socket.IO. `minSdk 24`.

A single `RadioPlaybackService : MediaLibraryService` owns the one ExoPlayer and
its `MediaLibrarySession`; the Compose UI binds through a `MediaController`, and
Android Auto browses the same session. The chat socket lives at application scope
so it survives navigation.

The interface uses shared Win9x components and palettes across every screen.
`ui/theme/Win98Theme.kt` and `ui/theme/Win98Palette.kt` define the common colours,
metrics and appearance for both light and dark modes.

Now playing, history and the upcoming queue come from the station's public
endpoints, along with the cover art. The app bundles no API keys or service
credentials. Features that require private credentials are handled by the
station's own server.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleRelease      # release build
```

Requires the Android SDK; open in Android Studio or set `sdk.dir` in
`local.properties`. Release signing is optional: without a `keystore.properties`
(see `keystore.properties.example`) the release build is simply unsigned, so the
project builds for anyone.

Unit tests cover stream parsers, the schedule builder, chat protocol and state,
emote parsing, nickname colours, theme derivation and VU meter arithmetic. The
playback, socket and Compose layers are exercised on a device.

## Roadmap toward 1.0

- **Localization:** French, Japanese, Spanish and German.
- **Personalization:** selectable desktop backgrounds and additional colour
  palettes.

## Privacy

The app ships no analytics, Google Play Services or Firebase. This can be checked
with:

```bash
./gradlew :app:dependencies | grep -i 'gms\|firebase\|analytics'   # expect no matches
```

The app requests internet access and the foreground-service, notification and
wake-lock permissions needed for playback and chat. The optional Maximum
reliability setting can ask Android for an exemption from battery optimizations
to keep the chat connected in the background. There is no microphone permission.
The VU meter reads the audio already being decoded instead of listening to the
room.

## Licence and attribution

The **source code** in this repository is licensed under the
[GNU GPL v3](LICENSE). That covers the code, and nothing else. The rest is worth
stating plainly.

**The music is not ours.** Rights in the tracks broadcast by Yumi Co. Radio belong
to their authors, performers and labels. This repository contains, redistributes
and licenses no music. The app plays the station's live streams and includes no
recordings.

**Cover art and track metadata** are fetched at runtime from the station's public
endpoints only. They belong to their respective owners and appear for
identification only.

**The W95FA typeface** (`app/src/main/res/font/w95fa.otf`) is by Alina Sava,
published through [FontsArena](https://fontsarena.com) under the SIL Open Font
License 1.1. See [`licenses/W95FA-OFL.txt`](licenses/W95FA-OFL.txt).

**The Windows 9x visual idiom** follows [98.css](https://github.com/jdan/98.css)
(MIT). "Windows" is a trademark of Microsoft Corporation; this project is not
affiliated with, endorsed by or connected to Microsoft, and is a tribute to the
era's interface design.

**Bundled icons and emotes** come from sets published by other artists. They stay
the property of their authors and are *not* covered by this project's GPLv3.
See [`ATTRIBUTION.md`](ATTRIBUTION.md) for the per-set sources and terms.

**The station's own name, logo and artwork** (`radio_logo.webp`, `ic_launcher_*`,
`default_cover.webp`) belong to Yumi Co. Radio and are likewise outside the
GPLv3 grant. Fork the code freely; please just don't ship a fork under the
station's identity.

Third-party brand marks (Last.fm, Bluesky, X) appear only as links to the
station's own profiles and remain the property of their owners.
