# oP Downloader

A native Android download manager built with **Kotlin + Jetpack Compose (Material 3)**.
It ships as **oP Downloader** with the purple `oP` brand mark, an Android 12+ splash
screen and the old `Android Downloader` visual language (aurora metric cards, floating
purple tab bar, dark/light palettes). The engine is real:

- **OkHttp direct downloads** — resumable (HTTP Range), progress/speed/ETA, queue with
  configurable concurrency.
- **yt-dlp + Python + FFmpeg** bundled via
  `io.github.junkfood02.youtubedl-android:library:0.18.1` and `:ffmpeg:0.18.1` —
  analyzes YouTube/TikTok/Instagram/Vimeo/etc., picks formats, merges to MP4 or MP3.
- Finished files are published to public **MediaStore** collections
  (`Movies/oP Downloader`, `Music/oP Downloader`, `Download/oP Downloader`)
  with an app-specific fallback when MediaStore refuses the MIME type.
- Foreground service + progress notification, pause/resume, retry, Wi-Fi-only mode,
  share-sheet intake, in-app WebView stream sniffer.

Package / applicationId: `com.opdownloader.app`. The older installs
`com.premiumdownloader` and `com.premiumdownloader.pro` remain separate apps;
their downloads and history stay untouched on the phone.

## Download

The latest signed production build is committed at the repo root as
[`app-release.apk`](app-release.apk) — **v1.1.0 (versionCode 11)**, package
`com.opdownloader.app`, signed with the app's release key. Install it over an
existing v9/v10 install with `adb install -r app-release.apk`.

## Build & install

Requirements: JDK 17+, Android SDK 36, a device/emulator on API 26+.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A signed release needs your own `keystore.properties` (same directory as
`build.gradle.kts`) and a `.jks` file. Both are intentionally gitignored and are
never published. Without them, use `:app:assembleDebug` for an unsigned debug build.

Wireless debugging:

```bash
adb connect <phone-ip>:<port>
adb devices -l
```

The dependency set is pinned to versions that build with **AGP 8.13.1 / compileSdk 36**
(Compose BOM `2025.06.01`, core-ktx `1.16.0`, core-splashscreen `1.0.1`,
activity-compose `1.10.1`, lifecycle `2.9.1`, navigation-compose `2.9.0`, Coil `2.7.0`). Newer BOMs require
AGP 9.x + compileSdk 37.

`useLegacyPackaging = true` is **required** so `libpython.zip.so` and the FFmpeg
binaries can be extracted at runtime. Do not remove it.

## Web Sniffer

The Browser tab is a full in-app browser (persistent cookies/logins, back/reload,
address bar) with a **floating sniffer** that stays above the floating tab bar:

- The WebView and the sniffer are laid out above `navigationBars + 90dp`, so neither
  the page nor the widget can sit under the tab bar or the system navigation bar.
- The sniffer pill is draggable inside the browser area and clamps its position to
  the visible bounds; tapping it opens the detected-video sheet.
- Detected candidates are de-duplicated and byte-probed on the server (`HEAD`, then a
  one-byte ranged `GET` when a CDN rejects `HEAD`). Known files under **150 KB** are
  hidden as thumbnails/ad bumps.
- The picker shows title, host, quality, and size, with **All / > 1 MB / > 5 MB /
  > 20 MB** size filters. HLS/DASH manifests, adaptive streams and watch pages are
  always kept because they carry no single file size.
- Watch-page detection covers YouTube (including Shorts), Instagram reels/posts,
  TikTok, X/Twitter, Vimeo, Dailymotion and several embed hosts; picking one opens the
  yt-dlp analyzer for the full multi-quality download.
- **Auto-skip ads** (toggle in the sniffer sheet): clicks visible Skip/Close ad
  controls, and during HTML5 ad reels mutes the player and raises playback rate to 16×
  until the ad container clears, then restores the user's mute/rate. It never seeks
  the video, so it cannot skip a user's actual content or trigger playlist autoplay.

## First run (automatic)

The app sets itself up; the user never needs to visit Settings:

1. Native Android 12+ splash → animated Compose splash with the `oP` logo.
2. Onboarding slides (first install only) → notification permission, then the
   battery-exemption dialog so downloads keep running with the screen off. Both
   prompts only appear on the final slide; the same choices can be changed later
   in **Settings → Downloads → Run in background**.
3. **Automatic engine setup screen** (`EngineSetupScreen`):
   `prepareStrict()` unpacks Python/FFmpeg, then `updateEngineStrict()` pulls the
   current STABLE yt-dlp release. Step rows show progress; failures retry once
   silently and then offer **Retry setup**.
4. On success `engineSetupDone = true` is persisted and the app enters Home. Later
   launches go splash → Home directly.

The same actions remain available in **Settings → Media engine** for manual recovery,
but they are no longer required for a new install. If the update cannot finish but the
runtime prepared, "Continue with the bundled engine" is available; it does not persist
the flag, so the update is retried on the next launch.

## Verified on device (2026-11-17)

Tested on a connected Android 15/API 37 arm64 device over wireless adb:

- App launch, home dashboard metrics, dark/light/system theme (persisted).
- Direct resumable download: `sample-5s.mp4` → Movies/oP Downloader, correct
  MediaStore row (`mime_type`, `is_pending=0`, duration).
- Document path: raw.githubusercontent.com README → Download/oP Downloader.
- yt-dlp analysis: 11 formats, thumbnail, title, duration, per-format sizes.
- Audio MP3 download → Music/oP Downloader (19 MB).
- 1080p DASH video download (148 MB after merge) with FFmpeg.
- **Pause/resume of a yt-dlp job**: paused at ~30 MB of partial data, resumed and the
  `.part` file grew 30.3 MB → 41.8 MB instead of restarting; completed successfully.
- **HLS VOD download through the Web Sniffer** (extractor-blocked site): analyzed a
  full `master.m3u8`, downloaded 72 segments via FFmpeg, published a 13:16 / 77 MB
  480p H.264/AAC MP4 (verified with `ffprobe`).
- **Restart recovery**: a wedged `DOWNLOADING` item became `PAUSED` after relaunch and
  resumed from its partial file.
- Quality picker ("View all"): chose 480p, CTA updated, downloaded 54 MB.
- Notification channel `download_progress`, notification id `1001`, live progress text.
- Files tab, bottom-sheet actions, Details screen (mime, engine, selector, timestamps),
  Share sheet (with `ClipData` so receivers get read access), Open with external player,
  and Delete with a confirmation that offers **delete file + list entry** or **list only**
  (list-only keeps the published file; card actions use a 2×2 grid so labels never clip).
- Web Sniffer: quick-open YouTube loads in-app; manual URL sheet; share-sheet intake.
  The icon+logo pill opens a draggable sniffer above the tab bar, lists size-probed
  streams with quality/size filters, and auto-skips player ads (the injected skip
  script was also exercised in headless Chromium: it clicked a hidden "Skip Ad"
  control and moved an ad video to 16×).
- Settings: theme, concurrency (1–5), Wi-Fi-only, progress notification, auto retry.
- **Branding**: launcher label `oP Downloader` (verified with `aapt2 dump badging`),
  legacy + round mipmaps at mdpi–xxxhdpi, adaptive icon (`mipmap-anydpi-v26`,
  white background + safe-zone logo), native splash theme and Compose splash. The Home
  top bar shows the purple vector mark (`drawable/ic_op_logo.xml`) beside **oP Downloader**.
- **Forced first-run setup**: with `engineSetupDone=false` the app showed "Setting up
  oP Downloader", prepared the runtime, reported `yt-dlp is up to date (2026.10.26)`
  and persisted completion; relaunching skipped setup and opened Home.
- **Live yt-dlp progress (fixed)**: a 720p merged DASH download showed real speed,
  ETA, byte counter and a moving bar instead of a silent 0% - `1.3-3.1 MB/s`,
  `ETA 02:15 → 00:55`, `4.0 → 73.8 MB`, completed and published at 97.5 MB.
  A 480p run displayed the estimate (`60.9 MB`), corrected it to `54.1 MB`
  (24.8 MB video + 29.3 MB audio) when the second stream started, and produced a
  54.3 MB merged file.
- **Fast analyze → download hand-off**: analysis runs `yt-dlp --dump-single-json`
  once and saves the raw JSON under `cacheDir/yt_info/<sha256>.json` (2 h TTL).
  The download reuses it with `--load-info-json`, so pressing Download no longer
  repeats the extraction. Measured on device: cold analysis `15.2 s`, cached
  re-analysis `27 ms`, download engine with cached info `2.2 s` vs `15.2 s` when
  re-extracting. A bad/expired JSON is deleted and retried once with a fresh
  extraction (verified by corrupting the cache). The analysis HEAD probe is skipped
  for known video hosts and uses an 8 s cap for everything else.

## Community & GitHub

- **Settings → Community → Star oP Downloader on GitHub ⭐** opens
  [github.com/iamrony27/opdownloader](https://github.com/iamrony27/opdownloader);
  the same card links **Bugs & feature requests** to the issue tracker.
- After **three days of use** the app shows a one-time "Enjoying oP Downloader?"
  dialog with **Star on GitHub** / **Later** / **Don't ask again**. The clock starts
  at first launch (`StarPrompt` in `data/StarPrompt.kt`, stored in the
  `op_star_prompt` preferences), "Later" snoozes it for a week, and opting out
  stops it permanently. It only appears on the main tabs, never during splash,
  onboarding or engine setup.

## Updates, background & missing files

- **Check for updates** appears in `Settings → About` and in the About screen's
  developer card (`ui/components/UpdateCheckRow.kt`). It calls the GitHub
  `releases/latest` API (`engine/UpdateChecker.kt`), compares dotted version
  numbers, and opens the release page when a newer tag exists. A 404 (the repo
  has no releases yet) is reported as **"No releases yet"**, not an error; network
  failures offer **Retry**.
- **Run in background** (`ui/components/BackgroundPermissionRow.kt`) requests the
  Android battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
  already declared in the manifest). On, downloads survive a locked screen; off
  opens the system settings list where the user can re-enable optimization.
- **Missing files**: a completed entry whose file was deleted from Files/Gallery
  shows a red **FILE DELETED** badge, "File no longer on device" in the card, and
  a warning in Details. `FilePublisher.fileExists()` confirms the MediaStore
  content URI (or the fallback file) is still readable before showing Open/Share.
  History entries are never auto-removed, and both delete choices are untouched.

## Project map

```
app/src/main/java/com/opdownloader/app/
  data/        models, DataStore-backed store, formatting helpers
  engine/      DirectDownloader (OkHttp), MediaTools (yt-dlp), DownloadManager,
               FilePublisher (MediaStore + fallback), UpdateChecker,
               notification helper
  service/     foreground DownloadService
  ui/          PremiumApp nav host, tab bar, shared components
  ui/screens/  Home, Active, Files, Browser, Settings, Analyzer, Quality,
               Details, Add, About, Onboarding, EngineSetup
```

Behavior notes:

- Queue concurrency is enforced in `DownloadManager.pump()`; Wi-Fi-only pauses queued
  items with a clear error instead of failing them.
- yt-dlp progress is read two ways: raw `[download]` lines are parsed in-app
  (percent/ETA/speed/total, because the library's legacy regex misses current
  output) and `cacheDir/ytdlp_<id>` is polled every 450 ms for real byte growth.
  The poller drives the UI, so speed/ETA/bar keep moving even for merged
  video+audio downloads where yt-dlp restarts its percentage for the audio stream.
  Per-stream totals also correct the analysis size estimate once the second stream
  begins.
- Pausing calls `YoutubeDL.destroyProcessById`; the engine's `CanceledException` is
  intentionally ignored when the item is already `PAUSED`. Resume reuses the same
  cache directory with `--continue`.
- yt-dlp output lives in `cacheDir/ytdlp_<id>` and is deleted after a successful
  publish. Pausing/removing keeps or clears it as appropriate.
- Extracted metadata lives in `cacheDir/yt_info/<sha256(url)>.json` (2 h TTL,
  pruned on write) and is fed to downloads via `--load-info-json`.
- HLS downloads are handed to the bundled **FFmpeg** (`--hls-prefer-ffmpeg`); the
  bundled Python TLS stack stalls on some CDNs. For `phncdn.com` / `sabtpledge.com`
  URLs the downloader also sends `Referer: https://www.pornhub.com/` — without it
  that CDN answers 404 for every segment.
- On process start, any transfer left as `QUEUED`/`DOWNLOADING` is marked `PAUSED`
  ("Interrupted by app restart") so a killed process can't leave a ghost job.
- `application/octet-stream` inserts are rejected by MediaStore Downloads on API 37;
  the publisher falls back to
  `/sdcard/Android/data/com.opdownloader.app/files/Download/oP Downloader/`
  and logs the reason under the `FilePublisher` logcat tag.

## Site notes: when the analyzer is blocked

Some extractors are fingerprinted server-side (PornHub currently answers yt-dlp's
page request with **HTTP 403**, while a normal browser session loads fine). Use the
**Web Sniffer** instead:

1. Browser tab → open the video page → wait for the media chips.
2. Tap the chip for the **full VOD** stream (a `master.m3u8` under `em-h.phncdn.com`
   or the 720P `index-v1-a1.m3u8`). Avoid `edge-hls...` playlists that start at a
   non-zero `#EXT-X-MEDIA-SEQUENCE` — those are live-edge windows (a few seconds).
3. Analyze → choose quality → download. Verified: a 13:16 video downloaded as a
   77 MB 480p H.264/AAC MP4.

If the WWW page loads in the sniffer but the direct analyzer still fails, that's the
extractor block, not the app. Cookie export from the WebView into yt-dlp
(`--cookies`) is the planned fix (see suggestions).

## Known limitations

- Release signing needs a local, uncommitted keystore; CI signing is not set up yet.
- No torrent/magnet support (FetchJet's torrent engine was not ported).
- Private/age-restricted YouTube videos need exported cookies
  (`--cookies` file). Not wired into the UI yet.
- Some sites require a browser session/JS runtime; yt-dlp may need `--extractor-args`
  or an updated engine.
- Sites that fingerprint the TLS client (PornHub's page endpoint currently returns
  403 to Python) can't be analyzed from the paste field; use the Web Sniffer flow
  above. Bundling `curl_cffi` impersonation would remove this limitation.
- MediaStore MIME guessing is extension-based; unknown extensions fall back to the
  app-specific folder (still accessible via the app, not the system Downloads app).
- The in-app sniffer is a convenience WebView; DRM-protected streams are out of scope.

## Suggested next steps

1. **Release signing CI** — GitHub Actions building a signed APK/AAB from secret-backed
   keystore material.
2. **Cookies import** for private videos and age gates, stored encrypted.
3. **Torrent support** — add the FetchJet torrent module or a libtorrent4j engine.
4. **Per-item scheduling / limits** — time windows, speed caps, battery-aware pause.
5. **Better metadata** — embed thumbnails/subtitles into media files, subtitle picker.
6. **MediaStore robustness** — probe MIME from content sniffing, not just extension;
   add a "convert unsupported container" option via FFmpeg.
7. **Background reliability** — WorkManager fallback for long queued jobs, battery
   optimization exemption prompt.
8. **Tests** — unit tests for `FilePublisher` MIME/category mapping and queue state
   machine; instrumented smoke test for enqueue → publish.
