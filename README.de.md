🌐 [English](README.md) | [Deutsch](README.de.md)

# ClipVault

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API 29+](https://img.shields.io/badge/API-29%2B-brightgreen)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%20You-Dynamic%20Colors-6750A4)](https://m3.material.io)
[![SQLCipher](https://img.shields.io/badge/SQLCipher-AES--256-blue?logo=sqlite&logoColor=white)](https://www.zetetic.net/sqlcipher/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-3.6.0-orange)](https://github.com/pepperonas/clipvault/releases)

<p align="center">
  <img src="clipvault.png" alt="ClipVault" width="400">
</p>

Android Clipboard-History-Manager mit Always-On-Verschlüsselung und optionaler App-Sperre.

## Keine INTERNET-Permission

ClipVault funktioniert **vollständig offline**. Die App hat die INTERNET-Permission explizit entfernt (`tools:node="remove"` im Manifest). Es werden keine Daten gesendet, keine Telemetrie erfasst, keine Server kontaktiert. Deine Clipboard-Daten verlassen niemals dein Gerät.

## Features

- **Zuverlässige Clipboard-Erfassung** via AccessibilityService (Listener + Event-Polling + Timer-Fallback, Mutex-geschütztes Debouncing)
- **Persistente Speicherung** in lokaler Room-Datenbank
- **Always-On-Verschlüsselung** — Datenbank immer mit SQLCipher (AES-256) verschlüsselt, auto-generierte 64-Zeichen-Passphrase im Android KeyStore (StrongBox bevorzugt)
- **Optionale App-Sperre** — Anzeige manuell sperrbar mit Fingerprint/Gesicht oder eigenem Passwort
- **Favoriten-Accordion** — Favorisierte Clips in aufklappbarer Sektion am Listenkopf
- **Swipe-to-Delete** mit Undo — Clips per Wisch-Geste löschen (40%-Schwelle gegen versehentliches Löschen), mit Rückgängig-Option
- **Content-Type-Icons** — Automatische Erkennung von Social-Media-Links (Instagram, Facebook, YouTube, X, TikTok, LinkedIn, GitHub), URLs, E-Mails, Telefonnummern
- **Integrierte Anleitung** — Hilfe-Dialog direkt in der App
- **Suche** und Clip-Verwaltung
- **Material You** (dynamische Farben ab Android 12) mit Dark/Light-Support
- **Foreground Service** mit persistenter Benachrichtigung
- **Keine Clip-Limits** — unbegrenzte Clips speichern

## Installation

### APK von GitHub

Signierte APKs (Release + Debug) stehen auf der [Releases-Seite](https://github.com/pepperonas/clipvault/releases) zum Download bereit.

**Wichtig (Android 13+):** Bei Installation per APK blockiert Android standardmäßig den Accessibility Service ("Eingeschränkte Einstellung"). So wird er freigeschaltet:

1. **Einstellungen > Apps > ClipVault** öffnen
2. Oben rechts auf **drei Punkte** tippen
3. **"Eingeschränkte Einstellungen zulassen"** wählen
4. Danach den Accessibility Service unter **Bedienungshilfen > ClipVault** aktivieren

> Dieser Schritt ist nur einmal pro Installation nötig und entfällt bei Installation über den Play Store.

### Voraussetzungen

- Android 10+ (API 29)
- AccessibilityService-Berechtigung (für Clipboard-Überwachung)
- Optional: Biometrische Hardware (für biometrische App-Sperre)

## Build

```bash
# Debug
./gradlew assembleDebug

# Release (minified + shrunk)
./gradlew assembleRelease

# Auf Gerät installieren
./gradlew installDebug

# Tests ausführen
./gradlew test
```

## Architektur

```
io.celox.clipvault/
├── ClipVaultApp.kt                  # Application — DB immer verschlüsselt öffnen
├── data/
│   ├── ClipEntry.kt                 # Room Entity
│   ├── ClipDao.kt                   # Room DAO
│   ├── ClipDatabase.kt              # Room DB (immer SQLCipher-verschlüsselt)
│   ├── ClipRepository.kt            # Repository (Dedup, Cooldown, Mutex)
│   └── DatabaseMigrationHelper.kt   # Plain -> Encrypted Migration
├── security/
│   └── KeyStoreManager.kt           # Android KeyStore (StrongBox) + Auto-Passphrase + App-Lock
├── service/
│   ├── ClipAccessibilityService.kt  # Clipboard-Capture (3 Strategien)
│   └── ClipVaultService.kt          # Foreground Service
└── ui/
    ├── theme/Theme.kt               # Material 3 Theme
    ├── history/
    │   ├── HistoryActivity.kt       # Hauptscreen
    │   └── HistoryViewModel.kt      # ViewModel
    ├── settings/SettingsActivity.kt  # Einstellungen (App-Sperre, Info)
    └── about/AboutActivity.kt       # Über die App
```

### Sicherheitsarchitektur

Die Datenbank ist **immer verschlüsselt** — es gibt keinen unverschlüsselten Modus:

- **DB-Passphrase**: 64 Zeichen, zufällig generiert via `SecureRandom`, gespeichert im Android KeyStore (AES-256-GCM)
- **StrongBox**: Auf Geräten mit dediziertem Secure Element (z.B. Samsung S24) wird der KeyStore-Key bevorzugt dort erzeugt
- **Byte-Zeroing**: Passphrase-Byte-Arrays werden nach Gebrauch genullt, um die Verweildauer im RAM zu minimieren
- **App-Sperre** (optional): Rein UI-seitig — sperrt die Anzeige, nicht die Datenbank. Zwei Modi:
  - *Fingerprint*: Auto-generiertes Passwort, Entsperrung nur via Biometrie/Geräte-PIN
  - *Eigenes Passwort*: Manuell gesetzt, optional mit Biometrie kombinierbar

### Datenfluss

1. **Clipboard-Erfassung**: AccessibilityService (3 Strategien, Mutex-Debouncing) -> ClipRepository.insert() (Mutex-serialisiert) -> verschlüsselte Room DB
2. **UI**: HistoryViewModel <- Flow<List<ClipEntry>> <- ClipDao
3. **App-Sperre**: HistoryActivity prüft `isAppLockEnabled` -> BiometricPrompt oder Passwort-Dialog

### Migration von v1/v2

Beim ersten Start nach dem Update auf v3 wird automatisch migriert:

- **v1/v2 (verschlüsselt mit User-Passwort)**: Legacy-Passwort wird als DB-Passphrase übernommen, App-Sperre wird aktiviert
- **v2 (unverschlüsselt)**: Datenbank wird mit auto-generierter Passphrase verschlüsselt
- **Frische Installation**: Datenbank wird direkt verschlüsselt erstellt

## Versionierung

Das Projekt verwendet [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking Changes (z.B. Architektur-Änderung)
- **MINOR**: Neue Features (rückwärtskompatibel)
- **PATCH**: Bugfixes

| Version | Änderung |
|---|---|
| 3.6.0 | Vollständige Zweisprachigkeit (Englisch + Deutsch), alle hardcodierten Strings in String-Ressourcen externalisiert |
| 3.5.0 | Open Source (MIT-Lizenz), In-App-Lizenzierung entfernt (keine Clip-Limits mehr), Unit-Tests hinzugefügt |
| 3.4.0 | Delete-Cooldown verhindert Re-Insert durch Polling, Toast-Spam entfernt, Umlaute korrigiert, README-Badges |
| 3.3.1 | Fix: oberster Eintrag löschbar (Swipe-Deletion nach Animation), kürzere Toasts, About-Seite mit Entwickler-Info und Website-Link |
| 3.3.0 | Content-Type-Icons (Social Media, URL, E-Mail, Telefon), Swipe-Schwelle 40% gegen versehentliches Löschen, Fix: letzter Eintrag löschbar |
| 3.2.0 | Undo nach Löschen, Anleitung, Bugfixes (Copy-Exception-Handling, Swipe-UX-Polish) |
| 3.1.1 | Clipboard-Erfassung: Mutex-Debouncing, Race-Condition-Fixes, Retry bei DB-Init, Error-Handling |
| 3.1.0 | Favoriten-Accordion, Swipe-to-Delete, persistente Benachrichtigung |
| 3.0.0 | Always-On-Verschlüsselung, App-Sperre statt optionaler DB-Verschlüsselung, StrongBox |
| 2.0.0 | Settings, Lizenzierung, optionale Verschlüsselung, About |
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

**Martin Pfeffer** -- [celox.io](https://celox.io)

## Lizenz

MIT License -- siehe [LICENSE](LICENSE).
