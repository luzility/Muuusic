<p align="center"><img src=".github/assets/banner.png" alt="Muuusic"></p>

<p align="center">An ad-free YouTube Music client for Android.</p>

## Features

- No ads, no account needed
- Background playback
- Synced lyrics from LRCLIB
- Likes, history and playlists, stored on the device
- Import YouTube / YouTube Music playlists from a link
- YouTube Music home feed with moods, and full artist pages
- Voice search and song recognition (experimental)
- Volume normalization, crossfade and an equalizer
- Skips non-music parts of music videos (SponsorBlock)
- Sleep timer
- Home screen widget
- Android Auto
- Picks up where you left off after the app is closed
- Download songs, albums and playlists for offline listening
- Played songs are cached

## Download

Get the APK from [Releases](https://github.com/luzility/Muuusic/releases). Betas are marked as pre-releases.

## Translations

Translations are done on [Weblate](https://hosted.weblate.org/engage/muuusic/). Pick your language there, or add a new one.

[![Translation status](https://hosted.weblate.org/widget/muuusic/multi-auto.svg)](https://hosted.weblate.org/engage/muuusic/)

## Building

Requires JDK 17+ and the Android SDK (API 37).

```bash
./gradlew assembleRelease
```

## Disclaimer

Muuusic is not affiliated with YouTube or Google. It talks to YouTube's public endpoints through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## License

[GPL-3.0](LICENSE). The bundled Roboto Flex font is under the [SIL Open Font License](licenses/RobotoFlex-OFL.txt).
