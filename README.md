# NovaMusic · Daylight Glass

**Android source project · v1.0.9 (six color themes; three interface styles; seven online music catalogues; local library kept separate).** Kotlin + Jetpack Compose + Material 3 + Media3 ExoPlayer/MediaSessionService + Room + DataStore + OkHttp. Application ID: `id.novamusic.app`. Minimum API 26, compileSdk 36, targetSdk 36.

## Build and verify

1. Open this repository in Android Studio with JDK 17 and Android SDK Platform 36 installed.
2. Sync Gradle using Gradle 8.13 (AGP 8.13.2).
3. Run `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` from the project root, or push to GitHub to run `.github/workflows/android.yml`.
4. Grant **Music and audio** permission to scan local audio. On Android 13+ this is `READ_MEDIA_AUDIO`; Android 8–12 uses `READ_EXTERNAL_STORAGE`.

**Build status:** Not verified in this editing environment because Android SDK and Gradle are unavailable. No debug APK is included or claimed as built. The GitHub Actions workflow is configured to compile, run unit tests, run Android lint, and upload the debug APK and reports after a successful CI run. Do not treat source review or ZIP integrity as proof of a successful APK build.

## Architecture

- `ui/`: Daylight Glass Compose screens and reusable components.
- `data/`: Room entities/DAO, MediaStore library, DataStore preferences.
- `network/`: seven online music catalogues (Audius, Internet Archive, Openverse, Jamendo via Openverse, Wikimedia Commons, ccMixter, Scott Buckley), LRCLIB lyrics, and per-source full-track/licence screening.
- `player/`: one Media3 ExoPlayer owned by `MediaSessionService`.
- `NovaViewModel`: UI state, asynchronous operations, player controller, queue coordination.
- `.github/workflows/`: Android build, unit-test, and lint pipeline.

## Refinement changes in v1.0.8

- Expanded Online search to seven catalogue sources: Audius, Internet Archive, Openverse, Jamendo indexed through Openverse, Wikimedia Commons, ccMixter, and Scott Buckley.
- All seven sources use public read/search access and do not require a developer Client ID or API key. Jamendo is accessed as an indexed Openverse source, not through Jamendo's credentialed first-party API.
- Openverse results require a direct audio-file URL, at least 90 seconds of indexed duration, and an accepted CC0/CC BY/CC BY-SA licence. The Jamendo-indexed subset uses the same checks.
- Wikimedia Commons results require a direct audio file, a file size of at least 1 MB, and a recognized permissive licence URL.
- ccMixter uses its public Query API RSS enclosure, requests Attribution-licensed uploads, and rejects items without a full-length duration or a sufficiently large original enclosure.
- Scott Buckley results are resolved to the explicitly labelled **MP3 (Full Mix)** download on the artist's own track page and retain CC BY 4.0 attribution details.
- Audius requires `is_streamable` and a duration of at least 90 seconds; Internet Archive requires an audio file of at least 1 MB and continues to apply its conservative rights filter. Preview-only links and embedded video/radio players are not used as audio sources.
- The Online screen identifies all seven catalogue sources. Device audio remains separate and is never merged into online results.
- Catalogues are independent/production-music and openly licensed collections. They do not guarantee current Indonesian chart hits or viral commercial releases; those require a separately licensed mainstream music provider.

## Refinement changes in v1.0.7

- Added Audius as an additional online music provider. Public read/search requests use the official Audius API without a developer API key; the app name is sent as a required identification parameter.
- Audius results are included alongside Internet Archive results in Online search. Tracks are shown only when Audius marks them `is_streamable`; playback uses the official Audius track stream endpoint through Media3, so Android background playback continues to use the existing MediaSession service.
- Audius tracks are not offered as downloads. Their track page and artist terms remain authoritative; streamability is not a blanket redistribution licence.
- YouTube is not integrated as a provider or player. No radio-only source was added. Device music remains separate from Online discovery.
- Audius is an independent-artist catalogue and may contain Indonesian creators, but it does not guarantee chart hits or viral commercial Indonesian releases.

## Refinement changes in v1.0.6

- Renamed the navigation destinations to **Online** and **Device** so streaming discovery and files stored on the phone have distinct entry points.
- The Online screen searches online catalogue providers; local MediaStore results are no longer mixed into online search results.
- Home listening history, recommendations, and recently added tracks now exclude on-device files. The Device screen remains the dedicated place for local tracks, albums, artists, folders, and genres.
- Added quick discovery searches for Indonesian music, Indonesian pop, dangdut, and Indonesian folk. These are search shortcuts, not claims that the catalogue contains current viral hits.
- Kept the conservative licence gate. Only recognizable CC0, CC BY, CC BY-SA, and public-domain declarations are eligible for streaming; a licence declaration is still not a guarantee of rights.
- No client ID, API key, YouTube/Spotify scraping, or paid-service bypass was added.

**Catalogue limitation:** Internet Archive is a legal open-licence/public-domain discovery source, not a mainstream chart service. It cannot guarantee full versions of currently viral Indonesian commercial songs. A dependable mainstream catalogue with those tracks generally requires a licensed provider and its approved integration. Do not label search shortcuts as "trending" without a real chart feed.

## Refinement changes in v1.0.5

- App volume is now persisted in DataStore with a short debounce, so dragging the volume slider does not trigger a write for every frame.
- On MediaController connection, saved volume, playback speed, shuffle, and repeat settings are applied even when no queue snapshot exists.
- Updated application and About versions to 1.0.5.

## Refinement changes in v1.0.4

- Queue restoration now loads saved track IDs in batches of 500 instead of issuing one potentially oversized SQLite `IN` query.
- This prevents bind-variable-limit failures when restoring unusually large queues while preserving the saved queue order and duplicate entries.
- Updated application and About versions to 1.0.4.

## Refinement changes in v1.0.3

- Sleep timer expiry now waits for the MediaController connection before attempting to pause playback and clearing the persisted deadline.
- This covers the cold-start race where a persisted timer expires before the media service connection is ready.
- Updated application and About versions to 1.0.3.

## Refinement changes in v1.0.2

- Fixed a loading-state race in online search: a cancelled, older request can no longer clear the loading indicator owned by a newer request.
- Search now marks itself busy immediately, including during the 300 ms debounce interval.
- Fixed the same cancelled-request race for lyrics loading when playback changes tracks quickly.
- Updated application and About versions to 1.0.2.

## Refinement changes in v1.0.1

- Corrected a mismatch between the documented rights policy and its implementation: Creative Commons declarations must use HTTPS. Plain HTTP declarations are rejected.
- License URLs containing fragments are rejected as non-canonical.
- Added a regression test for fragment-bearing license URLs. The existing test suite already covers HTTP declarations and lookalike hosts.
- Updated application and About versions to 1.0.1.

## v1.0.0 milestone

- Local-library refresh now removes stale local tracks in batches of 500 IDs, avoiding SQLite bind-variable limits on devices with large music collections.
- Keeps playlist, favorite, and listening-history metadata independent from the MediaStore rescan operation.
- Updated application and About versions to 1.0.0.

**Release status:** This is a v1.0.0 source milestone, not a verified production APK. A successful Android SDK build, unit-test run, lint report, and physical-device playback/permission/download checks are still required before calling the app production-ready.

## Refinement changes in v0.9.0

- Playlist insertion now reports duplicate tracks accurately. The Room insert returns its row ID, allowing the app to distinguish a newly added track from an ignored duplicate.
- Updated application and About versions to 0.9.0.
- Release-candidate checklist remains: run Android build, unit tests, lint, and device validation before declaring v1.0.0 stable.

## Refinement changes in v0.8.0

- Internet Archive requests now have explicit connect, read, and total-call timeouts.
- Archive search terms are normalized, limited to 100 characters, and stripped of query syntax and punctuation before being sent.
- In-memory metadata and search caches have size caps to prevent unbounded growth during long sessions.
- Updated the Archive metadata client user agent and app version.
- Added unit tests for query normalization, punctuation-only input, and maximum query length.

## Refinement changes in v0.7.0

- Sleep timer now stores an absolute expiry timestamp in DataStore rather than only keeping an in-memory coroutine delay.
- A configured timer is restored after the app process is recreated. Expired deadlines are cleared safely, and remaining minutes are rounded up for display.
- Added pure unit tests for timer expiry, rounding, and non-negative remaining time.
- Updated application and About versions to 0.7.0.

## Refinement changes in v0.6.0

- Fixed a malformed player import left in the previous source snapshot and removed a stale reference to an unshipped shuffle-order class.
- Extracted LRC timestamp parsing into a pure, testable parser. It supports centisecond and millisecond timestamps, long minute values, sorts out-of-order lines, and ignores malformed timestamps.
- Lyrics now have bounded network timeouts and a clearer NovaMusic user agent.
- Synced lyric lines are tappable: selecting a line seeks playback to that timestamp. Added a retry action when lyrics are unavailable.
- Updated the in-app About version and application version to 0.6.0.
- Added unit tests for LRC parsing edge cases.

## Refinement changes in v0.5.0

- Hardened asynchronous online search: 300 ms debounce, cancellation of superseded searches, stale-result clearing, and cancellation-safe loading-state cleanup.
- Hardened lyrics loading: requests for previous tracks are cancelled when the current media item changes, preventing stale lyrics from replacing the current track's lyrics.
- ViewModel cleanup cancels outstanding search, lyrics, sleep-timer, and media-scan jobs.
- Queue insertion uses a bounded Play Next index, and queue restoration clamps the saved current index to the restored item count.
- Added queue-index unit tests and bumped application version to 0.5.0.

## Refinement changes in v0.4.0

- Online search is debounced (300 ms), cancels superseded requests, clears stale results on failure, and always resets loading state.
- Lyrics requests are cancelled when the current track changes, preventing a slow response for an earlier track from overwriting the current track's lyrics.
- ViewModel cleanup now cancels outstanding search, lyrics, sleep-timer, and scan jobs.
- Added a small queue-index policy for deterministic Play Next insertion and safe queue restoration index clamping, with unit tests.
- Bumped application version to 0.4.0.

## Refinement changes in v0.3.0

- Rights screening now accepts only canonical Creative Commons HTTPS/HTTP URLs on the exact Creative Commons host, with CC0, Public Domain Mark, CC BY, and CC BY-SA paths. Free-text labels such as “Public Domain” are blocked because they are not reliable evidence.
- Added tests for lookalike hosts, credentials, ports, malformed URLs, unsupported versions, and NC/ND licenses.
- Internet Archive metadata no longer falls back from `licenseurl` to ambiguous free-text `rights`; missing license URLs remain blocked.
- Archive file selection now rejects zero/unknown-size, encrypted, private, and metadata-source entries.

## Stabilization changes in v0.2.0

- Tightened Internet Archive rights screening: only explicit CC0, CC BY, CC BY-SA, and public-domain declarations are allowed. NC, ND, unknown, malformed, and non-Creative-Commons URLs are blocked. This is a conservative metadata filter, not legal advice or a guarantee of rights.
- Added unit tests for allowed and rejected rights declarations.
- Queue checkpoints now include repeat mode, shuffle enabled state, playback speed, current index, and playback position.
- Player events now trigger queue checkpoints in addition to the periodic checkpoint.
- Queue restoration validates the saved repeat mode and playback speed and restores paused rather than unexpectedly autoplaying.
- Service task removal now clears playback and persists the cleared queue before stopping the service, avoiding a DataStore write race.
- Archive file selection rejects private/metadata entries, supports AAC, and falls back for blank metadata fields.
- CI now runs Android lint in addition to debug compilation and unit tests.

## Implemented capabilities

- Five-tab navigation and additional Playlist, Now Playing, Queue, Lyrics, and Downloads screens.
- Daylight Glass design system, launcher assets, animated splash, artwork/equalizer animations, mini-player, and queue controls.
- MediaStore local audio scan, Room catalog, favorites, playlists, listening history, and search history.
- Media3 player controls, MediaSession background playback, notification/lock-screen integration, audio focus/noisy handling, queue operations, shuffle/repeat, playback speed, volume, sleep timer, and queue persistence.
- Internet Archive search and metadata, separate online/local search results, and LRCLIB lyrics.
- DownloadManager integration for eligible Archive audio files, progress query, and cancellation.

## Known limitations before release

- No Gradle wrapper is committed; CI installs Gradle 8.13 explicitly. Add a standard Gradle Wrapper before distributing source outside GitHub/Android Studio.
- Shuffle mode is persisted, but Media3's exact internal randomized shuffle order is not serialized. A future queue-state format should persist a deterministic permutation and restore it inside the ExoPlayer-owning service through a Media3 `ShuffleOrder` implementation.
- No drag-and-drop queue reordering yet; current queue reordering uses functional move controls.
- No system audio-output picker or hardware-dependent equalizer/bass boost/virtualizer UI.
- DownloadManager does not expose a general pause/resume API; cancellation is supported.
- Sleep timer deadline is persisted, but exact behavior while the app process is killed must still be verified on physical devices and OEM battery-management configurations.
- Room is schema version 1. Add explicit migrations and exported schema snapshots before changing entities; never use destructive migration.
- Permission flows, Android 16 foreground-service behavior, notification controls, archive file URLs, and actual audio playback must be verified on Android devices/API levels 26–36.
- Unit tests and CI have been configured but have not been executed in this environment. The source snapshot also needs a real Android SDK build and device verification before it can be called a release.

## Rights and privacy

Do not assume all Internet Archive files are free to use. Metadata may be incomplete or incorrect. Verify the item's license and terms manually before redistribution. No Spotify/YouTube scraping, paid-service bypass, or embedded secret API keys are used.


## Appearance customization in v1.0.9

- Six persistent color themes: Daylight Glass, Midnight AMOLED, Ocean Breeze, Rose Quartz, Lavender Haze, and Sunset.
- Three interface styles: Glass, Minimal, and Compact. These adjust card shape, spacing, border treatment, and elevation.
- Theme and interface selections are saved with Android DataStore and restored on the next launch.
- Existing reduce-animation setting remains available.
