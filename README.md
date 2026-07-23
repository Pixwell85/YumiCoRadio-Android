# YumiCoRadio Android

Native Android player for [Yumi Co. Radio](https://yumicoradio.net), an amateur
24/7 net radio (Future Funk · City Pop · Nu Disco · Vaporwave), wrapped in an
authentic Windows 9x look.

No ads, no accounts, no analytics, no tracking.

## Features

**Player**

- Live stream at 256 kbps, 128 kbps (MP3) or 64 kbps (AAC).
- Background and lock-screen playback, with notification, Bluetooth and headset
  controls.
- Android Auto.
- Now playing: artist, title, cover art, elapsed time, bitrate, listener count.
- A real VU meter, measured from the decoded audio — no microphone permission.
- Sleep timer and share.

**Live chat**

- The station's chat: channels, private messages, emotes, file uploads with a
  quota, and presence (online / away / busy).
- Optional background connection. Notifications can be set to every message,
  mentions and PMs only, or none.
- Reserved nicknames are supported. The password is asked once per launch and
  never stored anywhere.

**Schedule and history**

- The current hour as a live bar, what is on now, and what is coming up.
- Recently played, with thumbnails.

**Appearance**

- Light and dark, both ported from the website's own themes.

## Tech

Kotlin · Jetpack Compose (Material3 restyled with Win9x tokens) · AndroidX Media3
(ExoPlayer + `MediaLibraryService`) · OkHttp · kotlinx.serialization · Coil ·
DataStore · Socket.IO. `minSdk 23`.

A single `RadioPlaybackService : MediaLibraryService` owns the one ExoPlayer and
its `MediaLibrarySession`; the Compose UI binds through a `MediaController`, and
Android Auto browses the same session. The chat socket lives at application scope
so it survives navigation.

The Windows 9x look is not approximated per screen. `ui/theme/Win98Theme.kt` and
`ui/theme/Win98Palette.kt` are the single source of the palette, metrics and type
scale: the greys are *derived* from one seed colour by the same arithmetic the
website uses, for both the light and the dark theme, and a test pins that
derivation so it cannot drift.

Now playing, history and the upcoming queue come from the station's public
endpoints, and so does the cover art. The app talks to no third-party service and
carries no credentials of any kind — anything needing a key is proxied by the station's own
server.

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

Tests cover the pure logic — stream parsers, the schedule builder, chat protocol
and state, emote parsing, nickname colours, the theme derivation and the VU
meter's arithmetic. The playback, socket and Compose layers are exercised on a
device.

## Privacy

The app ships no analytics, no Google Play Services, no Firebase — nothing that
would trip an F-Droid anti-feature flag. Verify with:

```bash
./gradlew :app:dependencies | grep -i 'gms\|firebase\|analytics'   # expect no matches
```

It requests no permission beyond what playback and chat need: network state,
foreground services, notifications and wake lock. In particular there is no
microphone permission — the VU meter reads the audio the app is already decoding
rather than listening to the room.

## Licence and attribution

The **source code** in this repository is licensed under the
[GNU GPL v3](LICENSE). That covers the code, and nothing else. The rest is worth
stating plainly.

**The music is not ours.** Yumi Co. Radio is an amateur net radio. Rights in the
tracks it broadcasts belong to their authors, performers and labels. This
repository contains, redistributes and licenses no music whatsoever — the app
plays a live stream and stores nothing.

**Cover art and track metadata** are fetched at runtime from the station's public
endpoints only. They belong to their respective owners and appear for
identification only.

**The W95FA typeface** (`app/src/main/res/font/w95fa.otf`) is by Alina Sava,
published through [FontsArena](https://fontsarena.com) under the SIL Open Font
License 1.1 — see [`licenses/W95FA-OFL.txt`](licenses/W95FA-OFL.txt).

**The Windows 9x visual idiom** follows [98.css](https://github.com/jdan/98.css)
(MIT). "Windows" is a trademark of Microsoft Corporation; this project is not
affiliated with, endorsed by or connected to Microsoft, and is a tribute to the
era's interface design.

**Bundled icons and emotes** come from sets published by other artists. They stay
the property of their authors and are *not* covered by this project's GPLv3 —
see [`ATTRIBUTION.md`](ATTRIBUTION.md) for the per-set sources and terms.

**The station's own name, logo and artwork** (`radio_logo.webp`, `ic_launcher_*`,
`default_cover.webp`) belong to Yumi Co. Radio and are likewise outside the
GPLv3 grant. Fork the code freely; please just don't ship a fork under the
station's identity.

Third-party brand marks (Last.fm, Bluesky, X) appear only as links to the
station's own profiles and remain the property of their owners.
