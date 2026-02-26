# ClipVault

Android Clipboard-History-Manager mit Always-On-Verschluesselung und optionaler App-Sperre.

## Features

- **Automatische Clipboard-Erfassung** via AccessibilityService (Listener + Event-Polling + Timer-Fallback)
- **Persistente Speicherung** in lokaler Room-Datenbank
- **Always-On-Verschluesselung** — Datenbank immer mit SQLCipher (AES-256) verschluesselt, auto-generierte 64-Zeichen-Passphrase im Android KeyStore (StrongBox bevorzugt)
- **Optionale App-Sperre** — Anzeige manuell sperrbar mit Fingerprint/Gesicht oder eigenem Passwort
- **Suche, Pinnen, Loeschen** von Clips
- **Material You** (dynamische Farben ab Android 12) mit Dark/Light-Support
- **Offline-Lizenzierung** (HMAC-SHA256, kein Internet erforderlich)
- **Foreground Service** mit persistenter Benachrichtigung

## Voraussetzungen

- Android 10+ (API 29)
- AccessibilityService-Berechtigung (fuer Clipboard-Ueberwachung)
- Optional: Biometrische Hardware (fuer biometrische App-Sperre)

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
├── ClipVaultApp.kt                  # Application — DB immer verschluesselt oeffnen
├── data/
│   ├── ClipEntry.kt                 # Room Entity
│   ├── ClipDao.kt                   # Room DAO
│   ├── ClipDatabase.kt              # Room DB (immer SQLCipher-verschluesselt)
│   ├── ClipRepository.kt            # Repository + Clip-Limit
│   └── DatabaseMigrationHelper.kt   # Plain -> Encrypted Migration
├── security/
│   └── KeyStoreManager.kt           # Android KeyStore (StrongBox) + Auto-Passphrase + App-Lock
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
    ├── settings/SettingsActivity.kt  # Einstellungen (App-Sperre, Lizenz, Info)
    ├── license/LicenseActivity.kt   # Lizenzaktivierung
    └── about/AboutActivity.kt       # Ueber die App
```

### Sicherheitsarchitektur

Die Datenbank ist **immer verschluesselt** — es gibt keinen unverschluesselten Modus:

- **DB-Passphrase**: 64 Zeichen, zufaellig generiert via `SecureRandom`, gespeichert im Android KeyStore (AES-256-GCM)
- **StrongBox**: Auf Geraeten mit dediziertem Secure Element (z.B. Samsung S24) wird der KeyStore-Key bevorzugt dort erzeugt
- **Byte-Zeroing**: Passphrase-Byte-Arrays werden nach Gebrauch genullt, um die Verweildauer im RAM zu minimieren
- **App-Sperre** (optional): Rein UI-seitig — sperrt die Anzeige, nicht die Datenbank. Zwei Modi:
  - *Fingerprint*: Auto-generiertes Passwort, Entsperrung nur via Biometrie/Geraete-PIN
  - *Eigenes Passwort*: Manuell gesetzt, optional mit Biometrie kombinierbar

### Datenfluss

1. **Clipboard-Erfassung**: AccessibilityService -> ClipRepository.insert() -> verschluesselte Room DB
2. **UI**: HistoryViewModel <- Flow<List<ClipEntry>> <- ClipDao
3. **App-Sperre**: HistoryActivity prueft `isAppLockEnabled` -> BiometricPrompt oder Passwort-Dialog
4. **Lizenz**: LicenseManager.validateAndActivate() -> KeyStoreManager.storeLicenseData()

### Migration von v1/v2

Beim ersten Start nach dem Update auf v3 wird automatisch migriert:

- **v1/v2 (verschluesselt mit User-Passwort)**: Legacy-Passwort wird als DB-Passphrase uebernommen, App-Sperre wird aktiviert
- **v2 (unverschluesselt)**: Datenbank wird mit auto-generierter Passphrase verschluesselt
- **Frische Installation**: Datenbank wird direkt verschluesselt erstellt

## Lizenzierung

Kostenlose Version: max. 10 Clips. Lizenzschluessel schaltet unbegrenzte Clips frei.

- **Algorithmus**: `HMAC-SHA256(email.lowercase().trim(), secret)`
- **Format**: `XXXX-XXXX-XXXX-XXXX` (erste 8 Bytes als Hex)
- **Validierung**: komplett offline, kein INTERNET-Permission
- **Key-Generierung**: `tools/generate-license-key.sh <email>`

## Versionierung

Das Projekt verwendet [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking Changes (z.B. Architektur-Aenderung)
- **MINOR**: Neue Features (rueckwaertskompatibel)
- **PATCH**: Bugfixes

| Version | Aenderung |
|---|---|
| 3.0.0 | Always-On-Verschluesselung, App-Sperre statt optionaler DB-Verschluesselung, StrongBox |
| 2.0.0 | Settings, Lizenzierung, optionale Verschluesselung, About |
| 1.0.0 | Initiale Version |

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
