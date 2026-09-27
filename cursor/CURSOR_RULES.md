# WatchBridge — Cursor Rules

Queste istruzioni guidano Cursor nello sviluppo di WatchBridge.
Leggile prima di iniziare qualsiasi sessione di sviluppo.

---

## Cos'è questo progetto

WatchBridge connette Apple Watch ad Android via Bluetooth Low Energy (BLE) diretto.
È composto da due app:
- **watchos/** — app Swift/SwiftUI per Apple Watch (watchOS 10+)
- **android/** — app Kotlin/Compose per Android (API 29+)

Leggi PLANNING.md per l'architettura completa, i milestone e le decisioni tecniche.

---

## Regole generali

- Scrivi codice **semplice e leggibile** — questo progetto è sviluppato con AI, deve essere comprensibile anche a chi non è esperto
- **Un file per responsabilità** — non mettere BLE + notifiche + UI nello stesso file
- Aggiungi **commenti in italiano** sui blocchi logici principali
- **Non inventare UUID BLE** — usa sempre quelli definiti in PLANNING.md o in BLEProtocol.swift/kt
- **Gestisci sempre gli errori** — BLE è instabile, ogni operazione può fallire
- Prima di scrivere codice, **spiega cosa farai** in 2-3 righe

---

## Regole watchOS (Swift)

### Stack obbligatorio
- Swift 6 con concorrenza strutturata (`async/await`, `@MainActor`)
- SwiftUI per tutta la UI
- CoreBluetooth per BLE (`CBCentralManager`, `CBPeripheral`)
- HealthKit per i dati salute

### Pattern da seguire

```swift
// ✅ CORRETTO — usa @Observable per i manager
@Observable
class BLEManager: NSObject {
    var isConnected = false
    var lastError: String?
}

// ❌ SBAGLIATO — non usare ObservableObject/Published su watchOS moderno
class BLEManager: ObservableObject {
    @Published var isConnected = false
}
```

```swift
// ✅ CORRETTO — gestisci sempre disconnessioni
func centralManager(_ central: CBCentralManager,
                    didDisconnectPeripheral peripheral: CBPeripheral,
                    error: Error?) {
    isConnected = false
    // Tenta reconnect dopo 2 secondi
    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
        self.startScanning()
    }
}

// ❌ SBAGLIATO — ignorare la disconnessione
func centralManager(_ central: CBCentralManager,
                    didDisconnectPeripheral peripheral: CBPeripheral,
                    error: Error?) { }
```

### Chunking BLE (IMPORTANTE)
Il MTU BLE è ~512 bytes. JSON più grandi vanno spezzati:

```swift
// Formato chunk: [index_byte, total_byte, ...payload]
func sendChunked(_ data: Data, to characteristic: CBCharacteristic) {
    let mtu = 510 // 512 - 2 byte header
    let chunks = stride(from: 0, to: data.count, by: mtu).map {
        data[$0..<min($0 + mtu, data.count)]
    }
    for (i, chunk) in chunks.enumerated() {
        var packet = Data([UInt8(i), UInt8(chunks.count)])
        packet.append(chunk)
        peripheral?.writeValue(packet, for: characteristic, type: .withResponse)
    }
}
```

### File e responsabilità watchOS
- `BLE/BLEManager.swift` — solo connessione BLE, scanning, write/read
- `BLE/BLEProtocol.swift` — UUID, tipi messaggi, serializzazione JSON
- `Health/HealthKitManager.swift` — solo lettura HealthKit
- `Notifications/NotificationView.swift` — solo UI notifiche
- `Media/MediaPlayerView.swift` — solo UI player musicale
- `Views/MainView.swift` — navigazione principale

---

## Regole Android (Kotlin)

### Stack obbligatorio
- Kotlin con coroutines (`suspend`, `Flow`, `CoroutineScope`)
- Jetpack Compose per UI
- Android BLE API (`BluetoothLeAdvertiser`, `BluetoothGattServer`)
- `NotificationListenerService` per le notifiche
- `Health Connect API` per la salute
- `Foreground Service` per mantenere BLE attivo

### Pattern da seguire

```kotlin
// ✅ CORRETTO — Foreground Service per BLE sempre attivo
class BLEPeripheralService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startGattServer()
        return START_STICKY // riavvia se il sistema lo killa
    }
}

// ❌ SBAGLIATO — BLE in un thread normale, viene killato da Android
class MainActivity : AppCompatActivity() {
    fun startBLE() { /* BLE qui viene fermato quando l'app va in background */ }
}
```

```kotlin
// ✅ CORRETTO — gestione permessi BLE su Android 12+
private fun checkBlePermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checkSelfPermission(BLUETOOTH_ADVERTISE) == PERMISSION_GRANTED &&
        checkSelfPermission(BLUETOOTH_CONNECT) == PERMISSION_GRANTED
    } else {
        checkSelfPermission(BLUETOOTH_ADMIN) == PERMISSION_GRANTED
    }
}
```

```kotlin
// ✅ CORRETTO — chunking e riassemblaggio
data class BleChunk(val index: Int, val total: Int, val payload: ByteArray)

fun ByteArray.toChunk(): BleChunk {
    return BleChunk(this[0].toInt(), this[1].toInt(), drop(2).toByteArray())
}
```

### File e responsabilità Android
- `ble/BLEPeripheralService.kt` — GATT server, advertising, foreground service
- `ble/BLEProtocol.kt` — UUID, tipi messaggi, serializzazione/deserializzazione
- `notifications/NotificationBridgeService.kt` — NotificationListenerService
- `media/MediaSessionBridge.kt` — lettura e controllo MediaSession
- `health/HealthConnectBridge.kt` — scrittura dati su Health Connect
- `ui/MainActivity.kt` — UI principale, stato connessione, settings

---

## Protocollo BLE — NON modificare questi UUID

```
// Copia questi in BLEProtocol.swift e BLEProtocol.kt

SERVICE_UUID      = "12345678-1234-1234-1234-123456789ABC"
CHARACTERISTIC_TX = "12345678-1234-1234-1234-123456789AB1"  // Watch → Android
CHARACTERISTIC_RX = "12345678-1234-1234-1234-123456789AB2"  // Android → Watch
```

### Tipi di messaggio JSON

```json
// Notifica (Android → Watch)
{"type": "notification", "id": "uuid", "app": "com.whatsapp",
 "appName": "WhatsApp", "title": "Marco", "body": "Ciao!", "timestamp": 1234567890}

// Risposta (Watch → Android)
{"type": "reply", "notificationId": "uuid", "text": "Ok!"}

// Stato media (Android → Watch)
{"type": "media_state", "title": "Song", "artist": "Artist",
 "isPlaying": true, "position": 45, "duration": 210}

// Comando media (Watch → Android)
{"type": "media_cmd", "action": "play|pause|next|prev"}

// Batch salute (Watch → Android)
{"type": "health_batch", "data": [
  {"metric": "steps", "value": 1234, "start": 1234567890, "end": 1234567891},
  {"metric": "heart_rate", "value": 72, "timestamp": 1234567890}
]}

// Ping/Pong (entrambe le direzioni)
{"type": "ping"}
{"type": "pong"}
```

---

## Prompt da usare per ogni Milestone

### Milestone 0 — Setup
```
Crea la struttura base del progetto WatchBridge.

Per watchOS:
- Nuovo progetto Xcode con target "Watch App" standalone (watchOS 10+, Swift 6)
- Bundle ID: com.watchbridge.app
- Crea i file vuoti con la struttura definita in CURSOR_RULES.md
- Aggiungi i permessi BLE e HealthKit in Info.plist
- ContentView.swift con testo "WatchBridge - Disconnesso"

Per Android:
- Nuovo progetto Android Studio (Kotlin, Compose, minSdk 29)
- Package: com.watchbridge
- Crea i file vuoti con la struttura definita in CURSOR_RULES.md
- Aggiungi tutti i permessi BLE in AndroidManifest.xml
- MainActivity con testo "WatchBridge - Disconnesso"

Non scrivere la logica BLE ancora, solo la struttura e i file vuoti.
```

### Milestone 1 — BLE Connection
```
Implementa la connessione BLE tra Watch e Android per WatchBridge.

Usa gli UUID in CURSOR_RULES.md. Segui i pattern Swift e Kotlin definiti lì.

watchOS — BLEManager.swift:
- CBCentralManager che scansiona per SERVICE_UUID
- Connessione al primo peripheral trovato
- Discovery di TX e RX characteristics
- Metodo sendPing() che invia {"type":"ping"} a Android
- Gestione didDisconnectPeripheral con retry automatico dopo 2 secondi
- Proprietà @Observable: isConnected, statusMessage

Android — BLEPeripheralService.kt:
- Foreground Service con notifica persistente "WatchBridge attivo"
- BluetoothLeAdvertiser con SERVICE_UUID
- BluetoothGattServer con TX e RX characteristics
- Quando riceve {"type":"ping"} risponde {"type":"pong"}
- Log ogni evento BLE importante

UI:
- Watch: mostra "Connesso ✓" o "Ricerca..." con animazione
- Android: mostra "Watch connesso" o "In attesa del Watch..."

Usa il chunking definito in CURSOR_RULES.md per tutti i messaggi.
Gestisci SEMPRE gli errori — non usare try! o force unwrap.
```

### Milestone 2 — Notifiche
```
Implementa il bridge notifiche Android → Apple Watch per WatchBridge.
La connessione BLE del Milestone 1 è già funzionante.

Android — NotificationBridgeService.kt:
- Estendi NotificationListenerService
- onNotificationPosted: serializza in JSON con formato da CURSOR_RULES.md
- Usa chunking BLE per inviare via BLEPeripheralService
- Lista app escluse di default: ["com.watchbridge", "android", "com.google.android.dialer"]
- Recupera appName da PackageManager

watchOS — NotificationView.swift:
- Ricevi JSON da BLEManager, decodifica in struct Notification
- Mostra lista notifiche recenti (max 20)
- Ogni riga: nome app (grassetto) + titolo + anteprima testo
- Scroll verticale con Digital Crown
- Tap su notifica → dettaglio completo

Testa con WhatsApp e Gmail come caso d'uso principale.
```

### Milestone 3 — Risposte rapide
```
Implementa le risposte rapide dal Watch per WatchBridge.
I Milestone 1 e 2 sono già funzionanti.

watchOS:
- Nel dettaglio notifica aggiungi pulsante "Rispondi"
- Sheet con 5 risposte predefinite: "Ok!", "Arrivo!", "Non posso ora", "👍", "📞 Ti richiamo"
- Pulsante dettatura vocale (usa SFSpeechRecognizer)
- Invia JSON reply come da CURSOR_RULES.md via BLE

Android — NotificationBridgeService.kt:
- Ricevi {"type":"reply", "notificationId":"...", "text":"..."}
- Trova la notifica con quell'ID in active notifications
- Usa RemoteInput API per inviare la risposta all'app originale
- Se RemoteInput non disponibile: mostra notifica "Risposta non supportata per questa app"

Testa con WhatsApp — è l'app con il miglior supporto RemoteInput.
```

### Milestone 4 — Musica
```
Implementa il controllo musica Android → Watch per WatchBridge.
I Milestone 1 e 2 sono già funzionanti.

Android — MediaSessionBridge.kt:
- MediaSessionManager per trovare sessioni attive
- Invia media_state ogni volta che cambia (titolo, artista, isPlaying, position)
- Ricevi media_cmd e chiama TransportControls.play/pause/skipToNext/skipToPrevious

watchOS — MediaPlayerView.swift:
- Tab dedicato "Musica" nella NavigationStack
- Titolo + artista centrati
- Pulsanti: ⏮ ⏸/▶ ⏭ (grandi, facili da toccare sul Watch)
- Barra progresso animata
- Se nessuna musica in riproduzione: "Nessuna musica in riproduzione"

Supporto principale: Spotify e YouTube Music.
```

### Milestone 5 — Salute
```
Implementa il sync dati salute Watch → Health Connect per WatchBridge.
Il Milestone 1 è già funzionante.

watchOS — HealthKitManager.swift:
- Richiedi autorizzazioni HealthKit per: passi, BPM, workout, sonno, calorie attive
- Ogni 5 minuti: leggi dati dell'ultima ora
- Serializza in health_batch JSON come da CURSOR_RULES.md
- Invia via BLE (chunked se necessario)

Android — HealthConnectBridge.kt:
- Ricevi health_batch, deserializza
- Scrivi su Health Connect: Steps, HeartRate, ExerciseSession, SleepSession
- Deduplicazione: prima di scrivere, controlla se esiste già un record nello stesso intervallo
- Log di ogni scrittura con successo/errore

Testa con una camminata di 10 minuti registrata sul Watch.
```

---

## Errori comuni da evitare

### watchOS
- **Non usare URLSession** su watchOS per comunicazioni Watch↔telefono — usa BLE diretto
- **Non chiamare HealthKit dal thread principale** — usa sempre `Task { }` o `DispatchQueue.global()`
- **CBCentralManager va creato sul main thread** ma i delegate callback arrivano su code diverse
- **watchOS non ha NotificationCenter.default** per alcune notifiche iOS — controlla la disponibilità

### Android
- **NotificationListenerService richiede permesso speciale** — non è un permesso runtime normale, l'utente deve andare in Impostazioni → Accesso alle notifiche
- **BLE Advertising richiede BLUETOOTH_ADVERTISE** su Android 12+ (API 31+)
- **Health Connect non è preinstallato** su tutti i dispositivi — gestisci il caso in cui non è disponibile
- **BluetoothGattServer callback arrivano su thread BLE** — usa `Handler(Looper.getMainLooper())` per aggiornare la UI

---

## Testing

### Senza hardware (simulatori)
- Milestone 0: tutto su simulatori
- Milestone 1: BLE limitato su simulatore — usa due Mac con Xcode per testare Watch↔iPhone via Bluetooth virtuale
- Da Milestone 2 in poi: **hardware reale obbligatorio**

### Con hardware reale
1. Build watchOS su Watch fisico via Xcode (iPhone connesso via cavo)
2. Build Android su telefono fisico via Android Studio
3. Avvicina Watch ad Android (~1 metro)
4. Controlla i log: Xcode Console per Watch, Logcat per Android

### Log utili
```swift
// watchOS — aggiungi sempre questo nei delegate BLE
print("[BLE Watch] \(#function): \(description)")
```

```kotlin
// Android — usa Log.d con tag costante
companion object { const val TAG = "WatchBridge" }
Log.d(TAG, "BLE: $event")
```
