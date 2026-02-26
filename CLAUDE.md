# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClipVault is an Android clipboard history manager (Kotlin, Jetpack Compose, Material 3). It uses an AccessibilityService to capture clipboard changes system-wide and stores them in an always-encrypted Room database (SQLCipher). The UI language is German.

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
3. Three capture strategies: listener (primary), accessibility event polling (fallback), periodic polling every 2s (Android 16+ fallback)
4. On each clipboard change, debounces (500ms same-text dedup), then inserts via `ClipRepository.insert()` which also deduplicates against the latest entry

### Data layer

- **Room database** (`clipvault.db`, version 1, single table `clip_entries`)
- **Always encrypted** with SQLCipher (AES-256). No plain-text mode exists.
- **ClipEntry**: `id`, `content`, `timestamp`, `pinned`
- **ClipDao**: queries return `Flow<List<ClipEntry>>`, includes `getCount()` for license checks
- **ClipRepository**: duplicate-check on insert + clip-limit enforcement for unlicensed users
- **DatabaseMigrationHelper**: plain-to-encrypted migration (for upgrades from older versions)

### Security layer

- **KeyStoreManager**: Central security manager handling two separate concerns:
  1. **DB passphrase**: Auto-generated 64-char passphrase, encrypted with AES-256-GCM via Android KeyStore (StrongBox preferred on API 28+). User never sees this.
  2. **App lock**: Optional UI lock with password (user-set or auto-generated) + biometric support. Stored separately from DB passphrase.
- **v3 migration**: On first launch after upgrade, legacy user password is adopted as DB passphrase (no re-encryption needed), and app lock settings are preserved.
- Passphrase byte arrays are zeroed after use (`Arrays.fill(bytes, 0)`)

### Licensing

- **LicenseManager**: offline HMAC-SHA256 validation, no INTERNET permission required
- Key format: `XXXX-XXXX-XXXX-XXXX` (16 hex chars from first 8 bytes of HMAC)
- Secret obfuscated via XOR'd byte arrays (not plain string in binary)
- Free tier: max 10 clips. Licensed: unlimited
- License data stored in KeyStoreManager's SharedPreferences
- Key generator: `tools/generate-license-key.sh <email>`

### UI layer

Multi-activity app with Jetpack Compose screens:

- **HistoryActivity** — main clipboard history (search, pin, copy, delete, lock/unlock)
- **SettingsActivity** — app lock toggle, biometric toggle, password change, license status, DB encryption info (always-on), about link
- **LicenseActivity** — email + key input with auto-formatting, activation feedback
- **AboutActivity** — app icon, version, author, copyright

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
