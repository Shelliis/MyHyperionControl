# MyHyperionControl

Eine Android-App zur Steuerung eines [Hyperion](https://github.com/hyperion-project/hyperion.ng) LED-Servers im lokalen Netzwerk.

## Features

- 🎨 **Farbrad** – HSV-Farbkreis zur intuitiven Farbauswahl mit Echtzeit-Übertragung beim Gleiten
- 💡 **Helligkeit** – stufenloser Slider (1–100 %)
- 🔘 **Ein / Aus** – LEDs ein- und ausschalten (Buttons nur aktiv wenn Server erreichbar)
- 🔍 **Automatische Serversuche** – mDNS-Discovery (`_hyperion._tcp.`) beim ersten Start
- ⚙️ **Manuelle Konfiguration** – Server-IP, Port und Priorität einstellbar
- 🔄 **Serverfarbe beim Start** – aktuelle Farbe wird beim App-Start vom Server gelesen und im Farbrad angezeigt
- 💾 **Farbe merken** – zuletzt verwendete Farbe wird gespeichert und beim nächsten Start wiederhergestellt
- 📶 **Verbindungsstatus** – grüner/roter Indikator, automatische Prüfung alle 15 Sekunden
- 🌑 **Dunkles Theme** – schwarzer Hintergrund, für den Einsatz im Heimkino optimiert
- 📱 **Portrait-only** – feste Ausrichtung im Hochformat

## Kommunikation

Die App kommuniziert über das **Hyperion Flat JSON TCP**-Protokoll – kein HTTP, kein WebSocket. Jeder Befehl wird als newline-terminiertes JSON-Objekt über einen Raw-TCP-Socket gesendet.

Unterstützte Befehle:

| Befehl | Funktion |
|---|---|
| `color` | RGB-Farbe mit Priorität setzen |
| `clear` | Priorität löschen (LEDs aus) |
| `serverinfo` | Aktuellen Farbzustand abfragen |

## Voraussetzungen

- Android 10 (API 29) oder neuer
- Hyperion-Server im selben WLAN-Netzwerk
- Flat JSON TCP Server in Hyperion aktiviert

## Build

```bash
# Debug (schnell, für Emulator und Entwicklung)
./gradlew assembleDebug

# Staging (schnell, für echte Geräte)
./gradlew assembleStaging

# Release (R8-Minifizierung, ~2,6 MB APK)
./gradlew assembleRelease
```

### Build-Typen

| Variante | Geschwindigkeit | APK-Größe | Verwendung |
|---|---|---|---|
| `debug` | ⚡ schnell | ~30 MB | Emulator + Debugger |
| `staging` | ⚡ schnell | ~30 MB | Echtes Gerät, tägliche Entwicklung |
| `release` | 🐢 langsam | ~2,6 MB | Finaler APK zum Verteilen |

## Technologie

- **Jetpack Compose** (Material 3)
- **Kotlin Coroutines** mit Throttle-Logik (80 ms) für flüssige Echtzeit-Updates
- **AndroidViewModel** + `mutableStateOf`
- **NsdManager** für mDNS-Discovery
- **SharedPreferences** für persistente Einstellungen und Farbspeicherung
- **R8** + Resource Shrinking für den Release-Build
