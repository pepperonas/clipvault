# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClipVault is an Android clipboard history manager (Kotlin, Jetpack Compose, Material 3). It uses an AccessibilityService to capture clipboard changes system-wide and stores them in a local Room database. The UI language is German.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (minified + shrunk)
./gradlew assembleRelease

# Install debug APK on connected device
./gradlew installDebug
```

No test suite exists currently. No linter is configured beyond the Kotlin compiler.

## Versioning

Semantic versioning (SemVer): `MAJOR.MINOR.PATCH` in `app/build.gradle.kts` (`versionName` + `versionCode`).

- `versionCode` increments with every release (monotonic integer for Play Store)
- `versionName` follows SemVer: breaking changes = MAJOR, new features = MINOR, fixes = PATCH

## Architecture

Manual dependency wiring — no DI framework. `ClipVaultApp` (Application subclass) holds singletons: `KeyStoreManager`, `LicenseManager`, `ClipDatabase`, `ClipRepository`.

### Clipboard capture flow

1. User enables the **AccessibilityService** (`ClipAccessibilityService`) in Android settings
2. `onServiceConnected` registers a `ClipboardManager.OnPrimaryClipChangedListener` and starts `ClipVaultService` (foreground service with persistent notification)
3. On each clipboard change, the listener debounces (500ms same-text dedup), then inserts via `ClipRepository.insert()` which also deduplicates against the latest entry
4. A Toast confirms capture to the user (or warns about clip limit if unlicensed)

### Data layer

- **Room database** (`clipvault.db`, version 1, single table `clip_entries`)
- **Dual-mode**: plain (default) or SQLCipher-encrypted (opt-in via Settings)
- **ClipEntry**: `id`, `content`, `timestamp`, `pinned`
- **ClipDao**: queries return `Flow<List<ClipEntry>>`, includes `getCount()` for license checks
- **ClipRepository**: duplicate-check on insert + clip-limit enforcement for unlicensed users
- **DatabaseMigrationHelper**: bidirectional migration (plain <-> encrypted)

### Security layer

- **KeyStoreManager**: AES-256-GCM encryption of DB passphrase via Android KeyStore, plus SharedPreferences for encryption/biometric/license flags
- **Encryption is optional** — toggled in Settings. When enabled: biometric unlock on resume, password fallback, auto-lock on `onStop`
- **Biometric**: toggleable independently (only when encryption is on)

### Licensing

- **LicenseManager**: offline HMAC-SHA256 validation, no INTERNET permission required
- Key format: `XXXX-XXXX-XXXX-XXXX` (16 hex chars from first 8 bytes of HMAC)
- Secret obfuscated via XOR'd byte arrays (not plain string in binary)
- Free tier: max 10 clips. Licensed: unlimited
- License data stored in KeyStoreManager's SharedPreferences

### UI layer

Multi-activity app with Jetpack Compose screens:

- **HistoryActivity** → main clipboard history (search, pin, copy, delete, lock/unlock)
- **SettingsActivity** → encryption toggle, password change, biometric toggle, license status, about link
- **LicenseActivity** → email + key input with auto-formatting, activation feedback
- **AboutActivity** → app icon, version, author, copyright

`HistoryViewModel` uses `flatMapLatest` to switch between all-entries and search-filtered flows.

### Key technical details

- **minSdk 29** (Android 10), **targetSdk/compileSdk 35**
- Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3
- KSP for Room annotation processing
- AppCompat DayNight theme for proper status bar icon adaptation
- Dynamic color (Material You) on Android 12+, custom dark/light fallback schemes
- Foreground service type: `specialUse` (clipboard_manager)
- ProGuard enabled for release builds (keeps SQLCipher + LicenseManager)
- INTERNET permission explicitly removed via manifest merger
