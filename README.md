# ClipVault

Android Clipboard-History-Manager mit optionaler Verschluesselung und Offline-Lizenzierung.

## Features

- **Automatische Clipboard-Erfassung** via AccessibilityService (Listener + Event-Polling + Timer-Fallback)
- **Persistente Speicherung** in lokaler Room-Datenbank
- **Optionale Verschluesselung** der Datenbank mit SQLCipher (AES-256)
- **Biometrische Sperre** (Fingerabdruck/Gesicht) oder Passwort-Fallback
- **Suche, Pinnen, Loeschen** von Clips
- **Material You** (dynamische Farben ab Android 12) mit Dark/Light-Support
- **Offline-Lizenzierung** (HMAC-SHA256, kein Internet erforderlich)
- **Foreground Service** mit persistenter Benachrichtigung

## Screenshots

*Kommt bald*

## Voraussetzungen

- Android 10+ (API 29)
- AccessibilityService-Berechtigung (fuer Clipboard-Ueberwachung)
- Optional: Biometrische Hardware (fuer biometrische Sperre)

## Build

```bash
# Debug
./gradlew assembleDebug

# Release (minified + shrunk)
./gradlew assembleRelease

# Auf Geraet installieren
./gradlew installDebug
```

## Architektur

```
io.celox.clipvault/
├── ClipVaultApp.kt                  # Application — DB + DI Wiring
├── data/
│   ├── ClipEntry.kt                 # Room Entity
│   ├── ClipDao.kt                   # Room DAO
│   ├── ClipDatabase.kt              # Room DB (plain + encrypted)
│   ├── ClipRepository.kt            # Repository + Clip-Limit
│   └── DatabaseMigrationHelper.kt   # Plain <-> Encrypted Migration
├── security/
│   └── KeyStoreManager.kt           # Android KeyStore + Prefs
├── licensing/
│   └── LicenseManager.kt            # Offline HMAC-SHA256 Validierung
├── service/
│   ├── ClipAccessibilityService.kt  # Clipboard-Capture (3 Strategien)
│   └── ClipVaultService.kt          # Foreground Service
└── ui/
    ├── theme/Theme.kt               # Material 3 Theme
    ├── history/
    │   ├── HistoryActivity.kt       # Hauptscreen
    │   └── HistoryViewModel.kt      # ViewModel
    ├── settings/SettingsActivity.kt  # Einstellungen
    ├── license/LicenseActivity.kt   # Lizenzaktivierung
    └── about/AboutActivity.kt       # Ueber die App
```

### Datenfluss

1. **Clipboard-Erfassung**: AccessibilityService -> ClipRepository.insert() -> Room DB
2. **UI**: HistoryViewModel <- Flow<List<ClipEntry>> <- ClipDao
3. **Verschluesselung**: ClipVaultApp.enableEncryption() -> DB schliessen -> migrieren -> encrypted oeffnen
4. **Lizenz**: LicenseManager.validateAndActivate() -> KeyStoreManager.storeLicenseData()

### Dual-Mode Datenbank

Die App startet standardmaessig **ohne Verschluesselung** (plain Room DB). Ueber Einstellungen kann SQLCipher-Verschluesselung aktiviert werden:

- **Plain → Encrypted**: DB schliessen, `sqlcipher_export` in verschluesselte DB, Passphrase in KeyStore speichern
- **Encrypted → Plain**: DB schliessen, `sqlcipher_export` in unverschluesselte DB, Passphrase loeschen
- **Passwort aendern**: Decrypt mit altem PW, Re-Encrypt mit neuem PW

## Lizenzierung

Kostenlose Version: max. 10 Clips. Lizenzschluessel schaltet unbegrenzte Clips frei.

- **Algorithmus**: `HMAC-SHA256(email.lowercase().trim(), secret)`
- **Format**: `XXXX-XXXX-XXXX-XXXX` (erste 8 Bytes als Hex)
- **Validierung**: komplett offline, kein INTERNET-Permission

## Versionierung

Das Projekt verwendet [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking Changes (z.B. DB-Schema-Aenderung, API-Inkompatibilitaet)
- **MINOR**: Neue Features (rueckwaertskompatibel)
- **PATCH**: Bugfixes

Version wird in `app/build.gradle.kts` gepflegt (`versionName` + `versionCode`).

## Tech Stack

| Komponente | Version |
|---|---|
| Kotlin | 2.0.21 |
| Jetpack Compose BOM | 2024.12.01 |
| Material 3 | via Compose BOM |
| Room | 2.6.1 |
| SQLCipher | 4.5.4 |
| AGP | 8.7.3 |
| Gradle | 8.11.1 |
| minSdk | 29 (Android 10) |
| targetSdk | 35 |

## Autor

**Martin Pfeffer** — [celox.io](https://celox.io)

## Lizenz

(c) 2026 Martin Pfeffer. Alle Rechte vorbehalten.
