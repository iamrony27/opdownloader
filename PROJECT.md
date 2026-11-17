# Project: PremiumDownloader

## Architecture
- **Platform**: Pure Native Android (Kotlin + Jetpack Compose Material 3), compileSdk 36, targetSdk 36, AGP 8.13.1.
- **UI Architecture**:
  - `MainActivity.kt`: Entry point with `enableEdgeToEdge()`.
  - `PremiumApp.kt`: Root composable with `NavHost` and `FloatingTabBar` aligned at `BottomCenter` with `navigationBarsPadding()`.
  - `BrowserScreen.kt`: Hosts in-app browser with `AndroidView(WebView)`, draggable sniffer widget, stream detection, probe client, and ad-skipping scripts.
- **Engine Subsystems**:
  - `Net.kt`: OkHttp clients (`okClient` and `probeClient`).
  - `DirectDownloader.kt` & `MediaTools.kt`: Chunked downloads and yt-dlp native extraction.
- **Device Target**: Wireless debugging Android device `AsteroidsIND` (model `A059`, Android 17 / SDK 37, 1080x2392 @ 420dpi).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Browser Window Display Sizing | Fit browser window within full display bounds excluding system and navigation bars, eliminate 10dp card padding and 20dp corner clipping, handle `imePadding` when keyboard appears | M1 | ORIGINAL_REQUEST §R1 |
| 2 | Free-Floating Sniffer Widget | Convert static `SnifferFab` into a stateful, draggable floating widget with smooth pointer drag gesture handling and tap disambiguation | M1 | ORIGINAL_REQUEST §R1 |
| 3 | Navigation Bar Clearance & Clamping | Restrict sniffer widget movement so it cannot overlap, touch, or slip underneath `FloatingTabBar` and system navigation bar | M1 | ORIGINAL_REQUEST §R1 |
| 4 | Network & DOM Stream Detection | Detect video streams via `shouldInterceptRequest`, `shouldOverrideUrlLoading`, and enhanced `SCAN_PAGE_JS` including XHR/fetch media | M2 | ORIGINAL_REQUEST §R2 |
| 5 | Video Size Probing & Validation | Probe candidate streams with mobile browser User-Agent, validate HTTP 200/206 status and media MIME types, extract accurate byte lengths | M2 | ORIGINAL_REQUEST §R2 |
| 6 | Size Threshold Stream Filtering | Filter candidate streams to exclude items smaller than `MIN_VIDEO_BYTES` (150 KB) and non-media items; dynamic filter chips (>1MB, >5MB, >20MB) | M2 | ORIGINAL_REQUEST §R2 |
| 7 | Website Player Ad Detection | Detect video ads across YouTube Mobile, IMA SDK, JW Player, Dailymotion, VideoJS using comprehensive ad container selectors | M3 | ORIGINAL_REQUEST §R3 |
| 8 | Player Skip Button Triggering | Trigger ad skip and overlay close buttons using synthetic MouseEvent click dispatch without manual intervention | M3 | ORIGINAL_REQUEST §R3 |
| 9 | Ad Fast-Forward & Sound Management | Fast-forward unskippable ads via `playbackRate = 16.0` while muted, restoring original speed and audio when ad completes | M3 | ORIGINAL_REQUEST §R3 |
| 10 | On-Device Build & Verification | Assemble APK, install on connected wireless device via ADB, verify window sizing, widget drag bounds, stream list filtering, and ad auto-skip | M4 | ORIGINAL_REQUEST §R4 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Browser Window & Floating Sniffer Widget Layout | Rectangular browser window fitting display bounds, IME padding, draggable sniffer widget with bounds clamping above navigation bar | none | DONE |
| 2 | Video Stream Detection & Size Filtering | Mobile User-Agent probe, status code validation, XHR/fetch resource timing scanning, strict >= 150KB size threshold filtering | M1 | DONE |
| 3 | Website Player Ad Auto-skipping | Enhanced `AD_SKIP_JS` selectors, synthetic event dispatch, fast-forwarding with rate/mute restoration, early injection | M2 | DONE |
| 4 | On-Device Deployment & E2E Verification | Gradle build, wireless ADB deployment, UI automator bounds verification, stream sniffing & ad skipping validation | M1, M2, M3 | DONE |

## Interface Contracts
### `BrowserScreen` ↔ `PremiumApp` (Layout Contract)
- `PremiumApp.FloatingTabBar`:
  - Visual height: `66.dp` + `14.dp` bottom padding + `16.dp` top padding = `96.dp`.
  - Insets: `WindowInsets.navigationBars.bottom`.
  - Effective reserved bottom clearance: `aboveTabs = maxOf(imeInset, navInset + 90.dp)`.
- `BrowserScreen`:
  - Browser container consumes remaining screen height above `aboveTabs`.
  - Draggable sniffer widget coordinates:
    - $X \in [\text{marginPx}, \text{containerWidth} - \text{fabWidth} - \text{marginPx}]$
    - $Y \in [\text{marginPx}, \text{containerHeight} - \text{fabHeight} - \text{marginPx}]$
    - Since `containerHeight` stops at `aboveTabs`, $Y + \text{fabHeight}$ can never breach the navigation bar zone.

### `Sniffer` ↔ `probeMedia` ↔ `filterVideos` (Data Contract)
- Data model `SniffedVideo`:
  - `url: String`
  - `sizeBytes: Long` (default 0L, populated by probe)
  - `mime: String?` (validated media MIME)
  - `playlist: Boolean` (true for m3u8/mpd)
  - `probing: Boolean` (transient loading state)
- Filter contract:
  - Non-playlist items must have `sizeBytes >= maxOf(150_000L, selectedFilterBytes)`.
  - Items with `sizeBytes <= 0L` are excluded unless actively probing (`probing == true`).

## Code Layout
- `app/src/main/java/com/opdownloader/app/ui/screens/BrowserScreen.kt`: Primary implementation file for browser layout, draggable sniffer widget, stream detection, probing, filtering, and ad-skipping.
- `app/src/main/java/com/opdownloader/app/ui/PremiumApp.kt`: Root scaffold and bottom navigation bar definition.
- `app/src/main/java/com/opdownloader/app/MainActivity.kt`: Window insets and edge-to-edge configuration.
- `scripts/`: E2E verification test runner scripts (ADB automated test harness).
