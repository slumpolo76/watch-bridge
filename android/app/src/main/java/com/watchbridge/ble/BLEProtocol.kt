package com.watchbridge.ble

import java.util.UUID

/**
 * UUID, tipi messaggi, serializzazione/deserializzazione JSON.
 */
object BLEProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789ABC")
    /** Watch → Android */
    val CHARACTERISTIC_TX: UUID = UUID.fromString("12345678-1234-1234-1234-123456789AB1")
    /** Android → Watch */
    val CHARACTERISTIC_RX: UUID = UUID.fromString("12345678-1234-1234-1234-123456789AB2")

    private const val CHUNK_PAYLOAD_MTU = 510

    fun pingMessage(): ByteArray = """{"type":"ping"}""".toByteArray(Charsets.UTF_8)

    fun pongMessage(): ByteArray = """{"type":"pong"}""".toByteArray(Charsets.UTF_8)

    fun chunk(data: ByteArray): List<ByteArray> {
        if (data.isEmpty()) {
            return listOf(byteArrayOf(0, 1))
        }
        val slices = data.toList().chunked(CHUNK_PAYLOAD_MTU).map { it.toByteArray() }
        val total = slices.size.coerceAtMost(255).toByte()
        return slices.mapIndexed { index, slice ->
            byteArrayOf(index.toByte(), total) + slice
        }
    }

    fun parseType(payload: ByteArray): String? {
        return try {
            val json = org.json.JSONObject(String(payload, Charsets.UTF_8))
            json.optString("type").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Riassembla i chunk in ingresso; restituisce il messaggio completo quando pronto.
     */
    class ChunkAssembler {
        private val buffer = mutableMapOf<Int, ByteArray>()
        private var expectedTotal: Int? = null

        fun add(packet: ByteArray): ByteArray? {
            if (packet.size < 2) return null
            val index = packet[0].toInt() and 0xFF
            val total = packet[1].toInt() and 0xFF
            if (total <= 0) return null

            if (expectedTotal == null || expectedTotal != total) {
                buffer.clear()
                expectedTotal = total
            }

            buffer[index] = packet.copyOfRange(2, packet.size)

            if (buffer.size != total) return null

            val assembled = ByteArray(buffer.values.sumOf { it.size })
            var offset = 0
            for (i in 0 until total) {
                val part = buffer[i] ?: return null
                part.copyInto(assembled, offset)
                offset += part.size
            }
            buffer.clear()
            expectedTotal = null
            return assembled
        }

        fun reset() {
            buffer.clear()
            expectedTotal = null
        }
    }
}
