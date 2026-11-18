# NovaMusic 1.4.0

Android music-library project built with Kotlin, Jetpack Compose, Media3, Room, DataStore, and OkHttp. Application ID: `id.novamusic.app`. Minimum API 26, compile/target SDK 36.

## Current providers

- **YouTube catalog:** official YouTube Data API search for music-category video metadata. Search results open in YouTube. The app does not extract or stream YouTube audio.
- **Spotify playlist metadata:** Android Authorization Code with PKCE login, callback validation, access-token refresh, and `GET /v1/playlists/{id}/items` import. Playlist access remains subject to Spotify's API permissions and account/app-mode restrictions. Spotify API metadata is not audio playback.
- **On-device music:** MediaStore local library and Media3 playback remain supported, including background playback through `MediaSessionService`.
- LRCLIB lyrics lookup remains available.

The previous online music providers (Audius, Internet Archive, Openverse/Jamendo, Wikimedia Commons, ccMixter, and Scott Buckley) and their provider-specific source code have been removed.

## Configuration

Copy the following values into the root `local.properties` file. Do not commit real credentials.

```properties
YOUTUBE_API_KEY=your_youtube_data_api_key
SPOTIFY_CLIENT_ID=a473f84507bf4ed4949a0ef292c695be
SPOTIFY_REDIRECT_URI=novamusic://spotify-callback
```

`YOUTUBE_API_KEY` is needed for catalog search. Create an app in the Spotify Developer Dashboard, copy its Client ID to `SPOTIFY_CLIENT_ID`, and add `novamusic://spotify-callback` exactly as a Redirect URI in that app's settings. The app uses Authorization Code with PKCE; do not create or embed a Spotify client secret in this Android app. The redirect URI configured in Spotify and `local.properties` must match exactly.

## Important implementation status

- YouTube search returns metadata only. Tapping a result opens the source in YouTube; it is not a background audio stream in NovaMusic.
- Spotify OAuth PKCE, state verification, token exchange, refresh-token renewal, and playlist pagination are implemented in `SpotifyRepository` and wired to the Online screen.
- Spotify content must not be downloaded or stream-ripped. Metadata and artwork must retain required Spotify attribution and links under Spotify's developer terms.
- No third-party provider scraping or paid-service bypass is included.
- OAuth tokens are stored in app-private preferences. Android app backup is disabled so credentials are not copied through the standard backup mechanism.

## Build and verify

Open the project in Android Studio with JDK 17 and Android SDK Platform 36. Run:

```bash
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

No Gradle wrapper is included. Build status has not been verified in this environment because Android SDK/Gradle are unavailable. The GitHub Actions workflow can build and upload a debug APK after a successful CI run.

## Architecture

- `ui/`: Compose screens and reusable components.
- `data/`: Room entities/DAO, MediaStore library, DataStore preferences.
- `network/`: YouTube catalog search, Spotify playlist metadata client, LRCLIB lyrics.
- `player/`: Media3 ExoPlayer owned by `MediaSessionService`.
- `NovaViewModel`: UI state, asynchronous operations, player controller, and queue coordination.


## Liquid Glass player update (1.4.0)

- Compact bottom music dock styled after the supplied reference: artwork, title, artist, player shortcut, add-to-playlist menu, and favorite toggle.
- The floating Liquid Glass orb remains draggable and opens Now Playing when tapped.
- The floating orb is shown when the Liquid Glass UI style is selected and a track is active.
