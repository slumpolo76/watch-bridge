import CoreBluetooth
import Foundation
import Observation

// Solo connessione BLE, scanning, write/read.
@MainActor
@Observable
final class BLEManager: NSObject {
    var isConnected = false
    var statusMessage = "Ricerca..."

    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var txCharacteristic: CBCharacteristic?
    private var rxCharacteristic: CBCharacteristic?

    private var pendingWriteChunks: [Data] = []
    private var incomingChunkBuffer: [UInt8: Data] = [:]
    private var incomingExpectedTotal: UInt8?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func startScanning() {
        guard centralManager.state == .poweredOn else {
            statusMessage = "Bluetooth non disponibile"
            return
        }
        isConnected = false
        statusMessage = "Ricerca..."
        peripheral = nil
        txCharacteristic = nil
        rxCharacteristic = nil
        centralManager.scanForPeripherals(withServices: [BLEProtocol.serviceUUID], options: nil)
        print("[BLE Watch] startScanning")
    }

    func sendPing() {
        guard let peripheral, let txCharacteristic else {
            print("[BLE Watch] sendPing: TX non pronta")
            return
        }
        pendingWriteChunks = BLEProtocol.chunk(BLEProtocol.pingPayload())
        writeNextChunk(to: peripheral, characteristic: txCharacteristic)
    }

    private func writeNextChunk(to peripheral: CBPeripheral, characteristic: CBCharacteristic) {
        guard let chunk = pendingWriteChunks.first else { return }
        peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
    }

    private func handleIncoming(_ data: Data) {
        guard let assembled = BLEProtocol.reassembleChunk(
            data,
            buffer: &incomingChunkBuffer,
            expectedTotal: &incomingExpectedTotal
        ) else {
            return
        }
        if BLEProtocol.messageType(from: assembled) == "pong" {
            statusMessage = "Connesso ✓"
            print("[BLE Watch] pong ricevuto")
        }
    }

    private func onReadyForMessaging() {
        isConnected = true
        statusMessage = "Connesso ✓"
        sendPing()
    }
}

extension BLEManager: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            switch central.state {
            case .poweredOn:
                startScanning()
            case .poweredOff:
                isConnected = false
                statusMessage = "Bluetooth spento"
            case .unauthorized:
                isConnected = false
                statusMessage = "Permesso Bluetooth negato"
            default:
                isConnected = false
                statusMessage = "Bluetooth non disponibile"
            }
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        Task { @MainActor in
            guard self.peripheral == nil else { return }
            central.stopScan()
            self.peripheral = peripheral
            peripheral.delegate = self
            statusMessage = "Connessione..."
            print("[BLE Watch] didDiscover: \(peripheral.identifier)")
            central.connect(peripheral, options: nil)
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            print("[BLE Watch] didConnect: \(peripheral.identifier)")
            peripheral.discoverServices([BLEProtocol.serviceUUID])
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            print("[BLE Watch] didFailToConnect: \(error?.localizedDescription ?? "unknown")")
            isConnected = false
            statusMessage = "Ricerca..."
            self.peripheral = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                self.startScanning()
            }
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            print("[BLE Watch] didDisconnectPeripheral: \(error?.localizedDescription ?? "none")")
            isConnected = false
            statusMessage = "Ricerca..."
            self.peripheral = nil
            txCharacteristic = nil
            rxCharacteristic = nil
            pendingWriteChunks.removeAll()
            incomingChunkBuffer.removeAll()
            incomingExpectedTotal = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                self.startScanning()
            }
        }
    }
}

extension BLEManager: CBPeripheralDelegate {
    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        Task { @MainActor in
            if let error {
                print("[BLE Watch] didDiscoverServices error: \(error.localizedDescription)")
                statusMessage = "Errore servizi"
                return
            }
            guard let service = peripheral.services?.first(where: { $0.uuid == BLEProtocol.serviceUUID }) else {
                print("[BLE Watch] servizio WatchBridge non trovato")
                statusMessage = "Servizio non trovato"
                return
            }
            peripheral.discoverCharacteristics([BLEProtocol.txUUID, BLEProtocol.rxUUID], for: service)
        }
    }

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        Task { @MainActor in
            if let error {
                print("[BLE Watch] didDiscoverCharacteristics error: \(error.localizedDescription)")
                statusMessage = "Errore caratteristiche"
                return
            }
            for characteristic in service.characteristics ?? [] {
                if characteristic.uuid == BLEProtocol.txUUID {
                    txCharacteristic = characteristic
                } else if characteristic.uuid == BLEProtocol.rxUUID {
                    rxCharacteristic = characteristic
                    peripheral.setNotifyValue(true, for: characteristic)
                }
            }
            guard txCharacteristic != nil, rxCharacteristic != nil else {
                statusMessage = "Caratteristiche mancanti"
                return
            }
            onReadyForMessaging()
        }
    }

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        Task { @MainActor in
            if let error {
                print("[BLE Watch] didUpdateValueFor error: \(error.localizedDescription)")
                return
            }
            guard characteristic.uuid == BLEProtocol.rxUUID, let data = characteristic.value else { return }
            handleIncoming(data)
        }
    }

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        Task { @MainActor in
            if let error {
                print("[BLE Watch] didWriteValueFor error: \(error.localizedDescription)")
                pendingWriteChunks.removeAll()
                return
            }
            guard !pendingWriteChunks.isEmpty else { return }
            pendingWriteChunks.removeFirst()
            if pendingWriteChunks.isEmpty {
                print("[BLE Watch] ping inviato")
                return
            }
            if let txCharacteristic {
                writeNextChunk(to: peripheral, characteristic: txCharacteristic)
            }
        }
    }
}
