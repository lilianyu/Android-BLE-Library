package nedprotocol

import nedprotocol.NedPacket.Companion.NED_REQ_UPGRADE
import java.nio.ByteBuffer

class NedPacket(var commandCode: Byte) {
    companion object {
        val HEAD: UShort  = 0xAA55u
        const val PAYLOAD_MAX_SIZE:UShort = 512u

        const val NED_REQ_UPGRADE = 0x01.toByte()
        const val NED_REQ_SEND_UPGRADE_PACKAGE = 0x02.toByte()
        const val NED_REQ_GET_DEVICE_INFO = 0x03.toByte()
        const val NED_REQ_GET_PLAIN_DATA = 0x04.toByte()

        const val NED_RESP_UPGRADE = 0xF1.toByte()
        const val NED_RESP_SEND_UPGRADE_PACKAGE = 0xF2.toByte()
        const val NED_RESP_GET_DEVICE_INFO = 0xF3.toByte()
        const val NED_RESP_GET_PLAIN_DATA = 0xF4.toByte()

        const val SIZE_PACKET_META_INFO = 9

        fun parsePacket(packet: ByteArray): NedPacket? {
            if (packet.isEmpty()) {
                throw NedParseException(-10000)
            }

            val buffer = ByteBuffer.wrap(packet)
            if (buffer.getShort(0).toUShort() != HEAD) {
                throw NedParseException(-10001)
            }

            var nedPacket = NedPacket(buffer.get(6))
            nedPacket.packetIndex = buffer.getShort(4).toInt()
            nedPacket.packetLength = buffer.getShort(2).toInt()
            nedPacket.payload = ByteArray(nedPacket.packetLength-9).apply { buffer.get(7) }

            return nedPacket
        }

        fun parseLength(packet: ByteArray): Int {
            if (packet.isEmpty()) {
                throw NedParseException(-10000)
            }

            val buffer = ByteBuffer.wrap(packet)
            if (buffer.getShort(0).toUShort() != HEAD) {
                throw NedParseException(-10001)
            }

            return buffer.getShort(2).toInt()
        }
    }

    var packetIndex = 0
    var packetLength = 0
    var payload: ByteArray? = null

    fun packet(payload: ByteArray, index:UShort = 0u): ByteArray {

        packetLength = SIZE_PACKET_META_INFO + payload.size
        packetIndex = index.toInt()
        this.payload = payload

        val fakeCRC:UShort = 0xFFFFu
        return ByteArray(packetLength).apply {
            ByteBuffer.wrap(this)
                .putShort(HEAD.toShort())
                .putShort(packetLength.toShort())
                .putShort(packetIndex.toShort())
                .put(commandCode.toByte())
                .put(payload)
                .putShort(fakeCRC.toShort())
        }
    }

}

data class NedParseException(val errorCode: Int): Exception()

fun main() {
    val nedRequest = NedPacket(NED_REQ_UPGRADE)
    val packet = nedRequest.packet(ByteArray(2) {
        if (it == 0) 'A'.code.toByte() else 'B'.code.toByte()
    })

    println(packet)


    val md5 = "AAAAABBBBBCCCCC".toByteArray()

    println(md5)

}
