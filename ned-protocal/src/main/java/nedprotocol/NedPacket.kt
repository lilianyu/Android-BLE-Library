package nedprotocol

import nedprotocol.NedPacket.Companion.NED_REQ_UPGRADE
import utils.CRC16Modbus
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

        val COMMAND_REQ_RESP_MAP = mapOf(NED_REQ_UPGRADE to NED_RESP_UPGRADE,
            NED_REQ_SEND_UPGRADE_PACKAGE to NED_RESP_SEND_UPGRADE_PACKAGE,
            NED_REQ_GET_DEVICE_INFO to NED_RESP_GET_DEVICE_INFO,
            NED_REQ_GET_PLAIN_DATA to NED_RESP_GET_PLAIN_DATA)

        const val SIZE_PACKET_META_INFO = 9

        fun parsePacket(packet: ByteArray): NedPacket? {
            if (packet.isEmpty()) {
                throw NedParseException(-10000)
            }

            val buffer = ByteBuffer.wrap(packet)
            if (buffer.getShort(0).toUShort() != HEAD) {
                throw NedParseException(-10001)
            }

            val length = parseLength(packet)

            val crc = CRC16Modbus()
            crc.update(packet!!, 0, length-2)
            val checkSum = crc.crcBytes
            if (checkSum[0]!=packet[length-2] || checkSum[1]!=packet[length-1]) {
                throw NedParseException(-10002)
            }

            return NedPacket(buffer.get(6)).apply {
                packetIndex = buffer.getShort(4).toInt()
                packetLength = buffer.getShort(2).toInt()
                payload = ByteArray(packetLength-9) {
                    packet[7+it]
                }

                this.packet = packet
            }
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
    var packet:ByteArray?=null

    fun load(payload: ByteArray, index:UShort = 0u): ByteArray {

        packetLength = SIZE_PACKET_META_INFO + payload.size
        packetIndex = index.toInt()
        this.payload = payload

        packet = ByteArray(packetLength).apply {
            ByteBuffer.wrap(this)
                .putShort(HEAD.toShort())
                .putShort(packetLength.toShort())
                .putShort(packetIndex.toShort())
                .put(commandCode)
                .put(payload)
        }

        val crc = CRC16Modbus()
        crc.update(packet!!, 0, packetLength-2)
        val checkSum = crc.crcBytes
        packet!![packetLength-2] = checkSum[0]
        packet!![packetLength-1] = checkSum[1]

        return packet!!

    }
}

data class NedParseException(val errorCode: Int): Exception()

fun main() {
    val nedRequest = NedPacket(NED_REQ_UPGRADE)
    val packet = nedRequest.load(ByteArray(2) {
        if (it == 0) 'A'.code.toByte() else 'B'.code.toByte()
    })

    println(packet)


    val md5 = "AAAAABBBBBCCCCC".toByteArray()

    println(md5)

}
