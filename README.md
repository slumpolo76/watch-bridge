# WatchBridge

**Use your Apple Watch with Android. Free, open source, forever.**

Connect Apple Watch directly to any Android phone over Bluetooth — no cloud,
no account, no subscription. Everything stays on your devices.

---

## Features

| Feature | Status |
|---|---|
| Android notifications → Apple Watch | 🚧 In progress (M2) |
| Quick replies from Watch | 📋 Planned (M3) |
| Music controls on Watch | 📋 Planned (M4) |
| Health data → Health Connect | 📋 Planned (M5) |

---

## How it works

WatchBridge creates a direct **Bluetooth Low Energy** connection between your
Apple Watch and your Android phone. No server in between, no data leaving
your devices.

```
Apple Watch  ←──── Bluetooth ────→  Android Phone
 watchOS app       (direct BLE)     WatchBridge app
```

> **One-time iPhone requirement.** Apple requires an iPhone to activate a new
> Apple Watch — this is a hardware constraint we cannot work around. You only
> need it once. After the initial setup, the iPhone can stay home or be
> returned if borrowed.

---

## Requirements

- Apple Watch Series 4 or later, watchOS 10+
- Android phone, Android 10+, with BLE
- Mac with Xcode 16+ (to install the Watch app)
- iPhone — one time only, for Watch activation

**EU users on iOS 26.5+:** Apple's iOS Notification Forwarding (mandated by
the Digital Markets Act) works alongside WatchBridge so you can receive both
iPhone and Android notifications on the Watch simultaneously.

---

## Install

### Watch app

WatchBridge is not on the App Store. Install it via Xcode:

```bash
git clone https://github.com/YOUR_USERNAME/watchbridge.git
cd watchbridge
open watchos/WatchBridge.xcodeproj
```

1. Connect your iPhone via USB
2. Select your Apple Watch as the build target in Xcode
3. Press `Cmd+R` to build and install

> Without a paid Apple Developer account, the app expires after 7 days and
> needs to be reinstalled. We are working on AltStore distribution to remove
> this step entirely.

### Android app

Download the APK from [Releases](https://github.com/YOUR_USERNAME/watchbridge/releases)
and install it on your Android phone. Grant the Notification Access and
Bluetooth permissions when prompted, then tap **Pair Watch**.

---

## Build from source

```bash
git clone https://github.com/YOUR_USERNAME/watchbridge.git

# watchOS — open in Xcode
open watchos/WatchBridge.xcodeproj

# Android — open in Android Studio
# File → Open → select the android/ folder
```

Milestone 0 and 1 work on simulators. From Milestone 2 onward, real hardware
is required (BLE notifications cannot be fully simulated).

---

## Project structure

```
watchbridge/
├── watchos/              # Apple Watch app (Swift + SwiftUI)
│   └── WatchBridgeWatch/
│       ├── BLE/          # CoreBluetooth — scan, connect, chunk
│       ├── Health/       # HealthKit reader
│       ├── Notifications/# Notification list + detail UI
│       └── Media/        # Music player UI
│
└── android/              # Android app (Kotlin + Jetpack Compose)
    └── app/src/main/java/com/watchbridge/
        ├── ble/          # GATT server, BLE advertising
        ├── notifications/# NotificationListenerService
        ├── media/        # MediaSession bridge
        └── health/       # Health Connect writer
```

See [PLANNING.md](PLANNING.md) for the full architecture, BLE protocol
specification, milestone breakdown, and technical decisions.

---

## Contributing

All contributions are welcome. The project is kept deliberately simple so
that anyone can contribute — including with AI-assisted coding tools like
Cursor or Copilot. Read [PLANNING.md](PLANNING.md) and
[CURSOR_RULES.md](CURSOR_RULES.md) before opening a PR.

**High-priority contributions:**
- BLE reconnection stability
- Notification app coverage
- Health data types
- AltStore manifest (eliminates the 7-day sideload problem for everyone)

**Have an Apple Developer account?** Open an issue — distributing via
TestFlight would remove the biggest installation barrier for non-technical
users.

---

## Reference projects

- [wangyendt/AppleWatch-BLE-Android](https://github.com/wangyendt/AppleWatch-BLE-Android)
  — BLE bridge demo: Watch as Central, Android as Peripheral
- [k97/WearBridge](https://github.com/k97/WearBridge)
  — iPhone → Wear OS via ANCS; excellent Kotlin BLE reference
- [abishekmuthian/apple-watch-with-android](https://github.com/abishekmuthian/apple-watch-with-android)
  — manual workarounds and real-world edge cases

---

## License

MIT. Do whatever you want with it.

---

## Status

🚧 **Active development.** Milestone 1 (BLE connection) in progress.

If this is useful to you, star the repo — it helps find contributors.
