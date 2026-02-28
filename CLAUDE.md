# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClipVault is an Android clipboard history manager (Kotlin, Jetpack Compose, Material 3). It uses an AccessibilityService to capture clipboard changes system-wide and stores them in an always-encrypted Room database (SQLCipher). Bilingual: English (default) + German. Licensed under MIT.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Debug build
./gradlew assembleRelease        # Release build (minified + shrunk, signed)
./gradlew installDebug           # Install debug APK on connected device
./gradlew test                   # Run all unit tests
./gradlew test --tests "io.celox.clipvault.data.ClipRepositoryTest"                    # Single test class
./gradlew test --tests "io.celox.clipvault.data.ClipRepositoryTest.insert new content returns new id"  # Single test method
```

**Testing stack**: JUnit 4 + Mockito Kotlin + kotlinx-coroutines-test. Tests use `runTest` and backtick-named methods.

## Versioning

SemVer in `app/build.gradle.kts` (`versionName` + `versionCode`). When bumping: update both fields in build.gradle.kts, version badge in both READMEs, and add a changelog row in both READMEs.

## Signing

Dual-source: environment variables (CI) or `local.properties` (local dev). Env vars: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## CI/CD

GitHub Actions (`.github/workflows/release.yml`): triggered by `v*` tags. Runs tests, builds signed release + debug APKs, uploads to GitHub Release. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Architecture

Manual dependency wiring — no DI framework. `ClipVaultApp` (Application subclass) holds singletons: `KeyStoreManager`, `ClipDatabase`, `ClipRepository`.

### Clipboard capture flow

1. User enables the **AccessibilityService** (`ClipAccessibilityService`) in Android settings
2. `onServiceConnected` registers a `ClipboardManager.OnPrimaryClipChangedListener` and starts `ClipVaultService` (foreground service with persistent notification)
3. Three capture strategies: listener (primary), accessibility event polling (fallback), periodic polling every 2s (Android 16+ fallback)
4. On each clipboard change, debounces (500ms same-text dedup), then inserts via `ClipRepository.insert()` which also deduplicates against the latest entry

### Data layer

- **Room database** (`clipvault.db`, version 1, single table `clip_entries`)
- **Always encrypted** with SQLCipher (AES-256). `SupportFactory(passphrase, null, false)` — third arg prevents premature passphrase clearing.
- **ClipEntry**: `id`, `content`, `timestamp`, `pinned`
- **ClipDao**: queries return `Flow<List<ClipEntry>>` for reactive UI; `getAllEntriesSnapshot()` (suspend, non-Flow) for export and statistics
- **ClipRepository**: mutex-serialized insert with duplicate-check, delete cooldown (see below), `exportAll()` + `importEntries()` with dedup by content+timestamp
- **DatabaseMigrationHelper**: plain-to-encrypted migration (for upgrades from older versions)

### Delete cooldown mechanism

Critical for preventing re-insertion of deleted content by the clipboard polling:

1. `setDeleteCooldown(content)` — **non-suspend**, sets `@Volatile` fields immediately
2. `delete(entry)` — **suspend**, only does the DB deletion
3. The ViewModel calls `setDeleteCooldown()` *before* launching the delete coroutine, closing the race window where polling could re-insert
4. Cooldown window: 10 seconds (`DELETE_COOLDOWN_MS`)
5. `reInsert()` clears the cooldown (for undo)

### DB recovery on reinstall

`ClipVaultApp.openDatabase()` forces a test query after opening. If the passphrase is wrong (e.g. after reinstall where KeyStore was cleared but DB file persists), it deletes the old DB and creates a fresh one.

### Security layer

- **KeyStoreManager**: Two separate concerns:
  1. **DB passphrase**: Auto-generated 64-char, encrypted with AES-256-GCM via Android KeyStore (StrongBox preferred on API 28+). User never sees this.
  2. **App lock**: Optional UI lock with password (user-set or auto-generated) + biometric support. Stored separately from DB passphrase.
- **v3 migration**: On first launch after upgrade, legacy user password is adopted as DB passphrase (no re-encryption needed), and app lock settings are preserved.
- Passphrase byte arrays are zeroed after use (`Arrays.fill(bytes, 0)`)

### Content type detection

`ContentTypeDetector.detectContentType()` classifies clipboard content into 18 types using pre-compiled regex patterns. Detection order matters — social media domains have highest priority, generic TEXT is fallback. Content types are computed on-the-fly (not stored in DB), so the enum can be extended without migration.

`ContentType` enum carries `icon` (Material icon), `color`, and `labelRes` (localized string resource ID). Used by filter chips, entry cards, smart actions, and statistics.

### Smart Actions

Long-press on a clip entry opens `SmartActionBottomSheet`. `resolveSmartActions()` maps each `ContentType` to available `SmartActionType` entries (COPY, OPEN_BROWSER, CALL, SEND_SMS, SEND_EMAIL, SHARE, OPEN_MAPS). Copy is always first, Share always last. Actions dispatch via Android intents in `HistoryActivity.handleSmartAction()`.

### Export/Import backup

`BackupCrypto` provides AES-256-GCM encryption with PBKDF2 key derivation (100k iterations, SHA-256). Binary format: `[CVBK magic][version][IV][salt][encrypted JSON]`. Salt and IV are random per export. JSON contains `version`, `exportedAt`, and `entries` array. Import deduplicates by content+timestamp. Settings UI uses SAF (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`) for file access.

### UI layer

Multi-activity app with Jetpack Compose screens:

- **HistoryActivity** — main clipboard history with:
  - `ContentTypeFilterBar`: horizontal `LazyRow` of `FilterChip`s, "All" + per-type chips sorted by count descending. Tap selected chip to deselect.
  - `SwipeableClipContainer`: bidirectional swipe — left=delete (dismisses card), right=pin/unpin (card stays visible, `confirmValueChange` returns `false`). Haptic feedback at 40% threshold.
  - Favorites accordion, search, overflow menu (Statistics, Guide, Settings)
- **StatisticsActivity** — one-shot snapshot via `getAllEntriesSnapshot()` in `LaunchedEffect`, no ViewModel. Canvas-drawn donut chart + bar chart.
- **SettingsActivity** — app lock, AMOLED toggle, backup export/import with password dialogs
- **AboutActivity** — app icon, version, author, copyright

`HistoryViewModel` uses `combine(unfilteredEntries, _selectedContentType)` to layer content type filter on top of search. `contentTypeCounts` is derived from unfiltered entries for chip badge counts.

### Foreground service notification

`ClipVaultService`: channel `clipvault_silent` with `IMPORTANCE_MIN` + `PRIORITY_MIN` for minimal visual presence. Uses `FOREGROUND_SERVICE_DEFERRED` to avoid prominence boost. Combined with `setSilent(true)`, `setOngoing(true)` + `FLAG_ONGOING_EVENT | FLAG_NO_CLEAR` to make the notification non-dismissible but silent. Legacy channel `clipvault_service` is deleted on start for cleanup. When changing channel importance, use a new channel ID — Android caches importance and won't lower it on an existing channel.

### Localization

- `values/strings.xml` — English (default)
- `values-de/strings.xml` — German
- All user-facing strings use `R.string.*` resources. Content type labels use `labelRes: Int` in enum. Timestamps use `Context.getString()` with format args. Android selects locale automatically.
- READMEs: `README.md` (English), `README.de.md` (German) with language switcher badges.

### Key technical details

- **minSdk 29** (Android 10), **targetSdk/compileSdk 35**
- Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3
- KSP for Room annotation processing
- AppCompat DayNight theme for proper status bar icon adaptation
- Dynamic color (Material You) on Android 12+, custom dark/light fallback schemes + AMOLED mode (true black)
- Foreground service type: `specialUse` (clipboard_manager)
- ProGuard enabled for release builds (keeps Room + SQLCipher classes)
- INTERNET permission explicitly removed via manifest merger
