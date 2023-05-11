package nedprotocol

import java.nio.ByteBuffer

object MockNedResponse {

    fun respPacketForUpgradeInfo(): ByteArray {
        val reserved:Short = 0
        val payloadSize = 2
        val payload = ByteArray(payloadSize).apply {
                ByteBuffer.wrap(this)
                    .putShort(reserved)
        }

        return NedPacket(NedPacket.NED_RESP_UPGRADE)
            .load(payload)
    }

    fun respPacketForDeviceInfo(): ByteArray {
        val payloadSize: Int = 24
        val hardwareVersion = 0xFF23A300u
        val softwareVersion = 0x00A323FFu
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .putInt(hardwareVersion.toInt())
                .putInt(softwareVersion.toInt())
                .put("49BA59ABBE56E057".toByteArray())
        }

        return NedPacket(NedPacket.NED_RESP_GET_DEVICE_INFO)
            .load(payload)
    }

    fun respPacketForUpgradePackage(req:NedPacket): ByteArray {
        val payloadSize: Int = 2
        val packetStatus = if (req.packet?.size==521) 0u else 2u
        val reserved = 0x00u
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .put(packetStatus.toByte())
                .put(reserved.toByte())
        }

        return NedPacket(NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE)
            .load(payload)
    }

    fun respPacketForPlainData(): ByteArray {
        val payloadSize = 5
        val brand = 0xA1u
        val plainData = 0x00FFA0B5u
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .put(brand.toByte())
                .putInt(plainData.toInt())
        }

        return NedPacket(NedPacket.NED_RESP_GET_PLAIN_DATA)
            .load(payload)
    }
}