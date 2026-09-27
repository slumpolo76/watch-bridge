import CoreBluetooth
import Foundation

// UUID, tipi messaggi, serializzazione JSON.
enum BLEProtocol {
    static let serviceUUID = CBUUID(string: "12345678-1234-1234-1234-123456789ABC")
    /// Watch → Android
    static let txUUID = CBUUID(string: "12345678-1234-1234-1234-123456789AB1")
    /// Android → Watch
    static let rxUUID = CBUUID(string: "12345678-1234-1234-1234-123456789AB2")

    private static let chunkPayloadMTU = 510

    static func pingPayload() -> Data {
        Data("{\"type\":\"ping\"}".utf8)
    }

    static func pongPayload() -> Data {
        Data("{\"type\":\"pong\"}".utf8)
    }

    /// Spezza un payload in pacchetti [index, total, ...payload].
    static func chunk(_ data: Data) -> [Data] {
        guard !data.isEmpty else {
            return [Data([0, 1])]
        }
        let step = chunkPayloadMTU
        let slices: [Data] = stride(from: 0, to: data.count, by: step).map { offset in
            data.subdata(in: offset ..< min(offset + step, data.count))
        }
        let total = UInt8(min(slices.count, 255))
        return slices.enumerated().map { index, slice in
            var packet = Data([UInt8(index), total])
            packet.append(slice)
            return packet
        }
    }

    /// Riassembla chunk; restituisce il messaggio completo quando pronto.
    static func reassembleChunk(_ packet: Data, buffer: inout [UInt8: Data], expectedTotal: inout UInt8?) -> Data? {
        guard packet.count >= 2 else { return nil }
        let index = packet[0]
        let total = packet[1]
        guard total > 0 else { return nil }

        if expectedTotal == nil || expectedTotal != total {
            buffer.removeAll(keepingCapacity: true)
            expectedTotal = total
        }

        buffer[index] = packet.dropFirst(2)

        guard (0 ..< Int(total)).allSatisfy({ buffer[UInt8($0)] != nil }) else { return nil }

        var assembled = Data()
        for i in 0 ..< Int(total) {
            assembled.append(buffer[UInt8(i)]!)
        }
        buffer.removeAll(keepingCapacity: true)
        expectedTotal = nil
        return assembled
    }

    static func messageType(from data: Data) -> String? {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = object["type"] as? String
        else {
            return nil
        }
        return type
    }
}
