# WatchBridge — Planning Document

## Goal

Build a free, open source app that lets anyone use Apple Watch with Android
as their primary phone. No subscriptions, no accounts, no cloud — everything
runs locally over a direct Bluetooth Low Energy connection.

---

## Background

- Apple Watch requires an iPhone for initial activation — this is an Apple
  hardware constraint that cannot be bypassed. It only needs to happen once.
- After pairing, Apple Watch can communicate directly with other devices via
  Bluetooth Low Energy (BLE), without the iPhone present.
- The EU's Digital Markets Act (DMA) forced Apple to open iOS notification
  access to third-party wearables starting with iOS 26.5 (EU only). This
  means WatchBridge users in the EU can also receive iPhone notifications
  on the Watch alongside Android ones.
- Anyone who owns both an iPhone and an Android phone is the ideal starting
  point: use the iPhone once for Watch setup, then use Android as the daily
  driver with the Watch fully connected.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│   Apple Watch (watchOS)                                 │
│   ┌─────────────────────────────────────────────────┐   │
│   │  WatchBridge watchOS App                        │   │
│   │  - Displays Android notifications               │   │
│   │  - Media controls                               │   │
│   │  - Quick replies                                │   │
│   │  - Sends health data                            │   │
│   └────────────────────┬────────────────────────────┘   │
│                        │ BLE (CoreBluetooth)             │
└────────────────────────┼────────────────────────────────┘
                         │
                         │ Bluetooth Low Energy
                         │ direct connection, no cloud
                         │
┌────────────────────────┼────────────────────────────────┐
│                        │                                 │
│   Android Phone        │                                 │
│   ┌─────────────────────────────────────────────────┐   │
│   │  WatchBridge Android App                        │   │
│   │  - NotificationListenerService                  │   │
│   │  - BLE Peripheral (GATT server)                 │   │
│   │  - Media session bridge                         │   │
│   │  - Health Connect sync                          │   │
│   └─────────────────────────────────────────────────┘   │
│                                                         │
└─────────────────────────────────────────────────────────┘

iPhone (one-time setup only)
- Required once to activate Apple Watch — Apple hardware constraint
- After setup: can stay at home, not needed for daily use
- EU users on iOS 26.5+: iPhone Notification Forwarding works alongside
  WatchBridge for receiving iPhone notifications on the Watch too
```

---

## Tech Stack

### Apple Watch (watchOS)

| Technology | Purpose |
|---|---|
| Swift 6 | Primary language |
| SwiftUI | Watch UI |
| CoreBluetooth | Direct BLE connection to Android |
| HealthKit | Read health data from Watch sensors |
| WatchConnectivity | Watch↔iPhone channel (optional, for EU notification forwarding) |
| Speech framework | Voice dictation for quick replies |

### Android

| Technology | Purpose |
|---|---|
| Kotlin | Primary language |
| Jetpack Compose | Android UI |
| NotificationListenerService | Intercept Android notifications |
| BluetoothLeAdvertiser / BluetoothGattServer | BLE GATT server toward Watch |
| MediaSession API | Read and control active media players |
| Health Connect API | Write health data from Watch |
| Foreground Service | Keep BLE alive in background |

### Communication protocol

- **Transport:** Bluetooth Low Energy — GATT
- **Roles:** Android = Peripheral (GATT Server), Watch = Central (GATT Client)
- **Data format:** JSON over BLE, chunked to fit BLE MTU (~512 bytes)
- **Security:** BLE bonding with PIN confirmation on first pair
- **No cloud:** all data stays on the two devices

---

## Reference Projects

| Repository | What we take from it |
|---|---|
| [wangyendt/AppleWatch-BLE-Android](https://github.com/wangyendt/AppleWatch-BLE-Android) | Foundation BLE architecture: CoreBluetooth Central on watchOS, BluetoothLeAdvertiser on Android |
| [k97/WearBridge](https://github.com/k97/WearBridge) | Kotlin NotificationListenerService patterns, BLE reconnection logic |
| [abishekmuthian/apple-watch-with-android](https://github.com/abishekmuthian/apple-watch-with-android) | Real-world edge cases and manual workaround techniques |

### Official documentation

**Apple:**
- [CoreBluetooth on watchOS — WWDC22](https://developer.apple.com/videos/play/wwdc2022/10135/)
- [HealthKit](https://developer.apple.com/documentation/healthkit)
- [WatchConnectivity](https://developer.apple.com/documentation/watchconnectivity)

**Android:**
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Bluetooth Low Energy](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect)
- [MediaSession](https://developer.android.com/media/media3/session/control-playback)

---

## BLE Protocol — UUIDs

These UUIDs are fixed for the lifetime of the project. Do not change them.

```
WatchBridge Service:
  UUID: "12345678-1234-1234-1234-123456789ABC"

Characteristics:
  TX (Watch → Android): "12345678-1234-1234-1234-123456789AB1"
  RX (Android → Watch): "12345678-1234-1234-1234-123456789AB2"

MTU: 512 bytes (negotiated at connect time)
Chunking: 2-byte header [chunk_index, total_chunks] + payload
```

### Message types (JSON)

```json
// Notification (Android → Watch)
{"type": "notification", "id": "uuid", "app": "com.whatsapp",
 "appName": "WhatsApp", "title": "Marco", "body": "Hey!", "timestamp": 1234567890}

// Quick reply (Watch → Android)
{"type": "reply", "notificationId": "uuid", "text": "On my way!"}

// Media state (Android → Watch)
{"type": "media_state", "title": "Song", "artist": "Artist",
 "isPlaying": true, "position": 45, "duration": 210}

// Media command (Watch → Android)
{"type": "media_cmd", "action": "play|pause|next|prev"}

// Health batch (Watch → Android)
{"type": "health_batch", "data": [
  {"metric": "steps", "value": 1234, "start": 1234567890, "end": 1234567891},
  {"metric": "heart_rate", "value": 72, "timestamp": 1234567890},
  {"metric": "workout", "activity": "running", "start": 123, "end": 456, "calories": 300}
]}

// Keepalive
{"type": "ping"}
{"type": "pong"}
```

---

## Milestones

### Milestone 0 — Project Setup
**Goal:** repository structure in place, both apps build and run on simulators.

Tasks:
- GitHub repo with monorepo layout (`/watchos`, `/android`)
- Xcode project with standalone watchOS target (no iOS companion required)
- Android Studio project with Kotlin + Jetpack Compose
- BLE permissions declared on both platforms
- "Hello World" UI on both apps: just a connection status label

Deliverable: both apps build without errors and run on their respective simulators.

---

### Milestone 1 — BLE Connection
**Goal:** Watch and Android find each other, connect, and exchange a test message.

Tasks:
- Android starts GATT server and advertises the WatchBridge Service UUID
- Watch scans, discovers Android, connects and discovers characteristics
- Watch sends `{"type":"ping"}`, Android responds `{"type":"pong"}`
- Auto-reconnect on disconnect (exponential backoff, max 30s)
- Minimal UI on both: "Connected ✓" / "Searching…"

Deliverable: reliable ping/pong over BLE between Watch and Android.

---

### Milestone 2 — Android Notifications → Watch
**Goal:** every notification received on Android appears on the Watch in real time.

Tasks:
- Android: `NotificationListenerService` intercepts posted notifications
- Android: serialize to JSON (see message types above)
- Android: chunk large payloads and send over BLE TX characteristic
- Android: Foreground Service keeps BLE alive when app is in background
- Watch: reassemble chunks, decode JSON, display as local notification
- Watch: notification list UI (max 20 items), scrollable with Digital Crown
- Watch: per-app filter (include/exclude list, configurable in Settings)

Default excluded apps: system apps, WatchBridge itself.

Deliverable: WhatsApp, Gmail, and Telegram notifications appear on Watch.

---

### Milestone 3 — Quick Replies
**Goal:** reply to messages directly from the Watch.

Tasks:
- Watch: "Reply" button on notification detail screen
- Watch: sheet with 5 preset replies + emoji picker
- Watch: voice dictation via `SFSpeechRecognizer`
- Watch: send `reply` JSON via BLE
- Android: receive reply, locate active notification by ID, send via `RemoteInput` API
- Android: fallback notification if app doesn't support RemoteInput

Deliverable: reply to WhatsApp messages from the Watch.

---

### Milestone 4 — Music Controls
**Goal:** control Android media playback from the Watch.

Tasks:
- Android: `MediaSessionManager` reads active player state
- Android: send `media_state` to Watch on every change (track, play/pause, position)
- Watch: dedicated Music tab with track info, play/pause, next, previous
- Watch: progress bar (updated every second while playing)
- Watch: send `media_cmd` back to Android on button tap
- Android: execute command via `TransportControls`

Primary targets: Spotify, YouTube Music, Pocket Casts.

Deliverable: Play/Pause/Next/Prev working from Watch for Spotify.

---

### Milestone 5 — Health Data Watch → Android
**Goal:** Apple Watch health data flows into Health Connect on Android.

Tasks:
- Watch: request HealthKit authorization (steps, heart rate, workouts, sleep, active calories)
- Watch: read data for the last hour every 5 minutes
- Watch: send `health_batch` via BLE (chunked)
- Android: deserialize batch, write to Health Connect
- Android: deduplication — check for existing records in the same time range before writing
- Android: support exercise types: walking, running, cycling, swimming, strength training

Deliverable: steps and workouts from the day visible in Health Connect.

---

### Milestone 6 — Stability & Polish
**Goal:** app ready for real daily use.

Tasks:
- Robust BLE reconnect (BLE drops frequently in real conditions)
- Battery optimization: tune connection interval per activity level
- Android UI: pairing screen, connection status, per-app notification toggle
- Watch complication: connection status dot (green/grey)
- End-to-end test on real hardware across a full day
- User-facing setup guide (non-technical language)
- AltStore / SideStore manifest so users without a Mac can install the Watch app

Deliverable: the app works reliably through a normal day without manual intervention.

---

## Repository Structure

```
watchbridge/
├── README.md
├── PLANNING.md              ← this file
├── CURSOR_RULES.md          ← AI coding assistant instructions
├── .gitignore
│
├── watchos/                 ← Apple Watch app (Swift + Xcode)
│   ├── WatchBridge.xcodeproj
│   ├── WatchBridgeApp/      ← thin iOS companion (required by Xcode)
│   │   ├── WatchBridgeApp.swift
│   │   └── Info.plist
│   └── WatchBridgeWatch/    ← actual watchOS target
│       ├── BLE/
│       │   ├── BLEManager.swift       # scan, connect, read/write
│       │   └── BLEProtocol.swift      # UUIDs, JSON types, chunking
│       ├── Health/
│       │   └── HealthKitManager.swift # read HealthKit, send batches
│       ├── Notifications/
│       │   └── NotificationView.swift # list + detail UI
│       ├── Media/
│       │   └── MediaPlayerView.swift  # player UI + commands
│       └── Views/
│           ├── MainView.swift         # tab navigation
│           └── SettingsView.swift     # app filters, quick replies
│
└── android/                 ← Android app (Kotlin + Android Studio)
    └── app/src/main/
        ├── java/com/watchbridge/
        │   ├── ble/
        │   │   ├── BLEPeripheralService.kt  # GATT server, advertising, foreground
        │   │   └── BLEProtocol.kt           # UUIDs, JSON, chunking
        │   ├── notifications/
        │   │   └── NotificationBridgeService.kt  # NotificationListenerService
        │   ├── media/
        │   │   └── MediaSessionBridge.kt    # MediaSession reader + controller
        │   ├── health/
        │   │   └── HealthConnectBridge.kt   # Health Connect writer
        │   └── ui/
        │       └── MainActivity.kt          # main UI, pairing, settings
        └── AndroidManifest.xml
```

---

## Required Permissions

### watchOS — Info.plist
```xml
NSBluetoothAlwaysUsageDescription
NSHealthShareUsageDescription
NSSpeechRecognitionUsageDescription
NSMicrophoneUsageDescription
```

### Android — AndroidManifest.xml
```xml
BLUETOOTH_ADVERTISE          (API 31+)
BLUETOOTH_CONNECT            (API 31+)
BLUETOOTH_SCAN               (API 31+)
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
BIND_NOTIFICATION_LISTENER_SERVICE
READ_MEDIA_IMAGES
health_connect read/write permissions
```

---

## Known Limitations

- **iPhone required for Watch activation.** This is an Apple hardware constraint.
  It only needs to happen once; the iPhone can stay at home afterward.
- **7-day sideload expiry** without a paid Apple Developer account ($99/year).
  The Watch app must be reinstalled via Xcode every 7 days. AltStore distribution
  is planned for Milestone 6 to remove this friction.
- **BLE background on watchOS.** watchOS aggressively suspends BLE when the app
  is not in the foreground. We mitigate this with BGTaskScheduler and keepalive
  pings, but deep sleep may cause short gaps in notification delivery.
- **Notification latency.** Typical BLE round-trip: 100–500ms. Acceptable for
  notifications, not suitable for real-time audio or video.
- **EU-only for iPhone notifications.** iOS Notification Forwarding (iOS 26.5+)
  is only available in the EU due to DMA. WatchBridge Android notifications work
  everywhere regardless.

---

## Future Roadmap (post v1.0)

- Contact sync (Android contacts available on Watch for calls)
- Find My — ring Android from Watch and vice versa
- Calendar complications showing next Android calendar event
- Multi-phone support (switch between iPhone and Android sources)
- Custom quick-reply keyboard on Watch
- TestFlight distribution (requires Apple Developer account donation from community)
- Wear OS support (reverse direction: Wear OS watch with iPhone)
