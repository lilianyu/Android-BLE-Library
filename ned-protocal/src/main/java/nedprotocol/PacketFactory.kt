package nedprotocol

import java.nio.ByteBuffer

object PacketFactory {
    fun packetForUpgradeInfo(packageSize:Int, versionCode:Int, md5:ByteArray): ByteArray {
        val reserved:Short = 0
        val payloadSize: Int = 4 + 4 + 2 + md5.size
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .putInt(packageSize)
                .putInt(versionCode)
                .put(md5)
                .putShort(reserved)
        }

        return NedPacket(NedPacket.NED_REQ_UPGRADE)
                .packet(payload)
    }

    fun packetForPackage(payload:ByteArray, index:UShort): ByteArray {
        return NedPacket(NedPacket.NED_REQ_SEND_UPGRADE_PACKAGE)
            .packet(payload, index)
    }

    fun packetForDeviceInfo(): ByteArray {
        val reserved:Short = 0
        val payloadSize = 2
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .putShort(reserved)
        }

        return NedPacket(NedPacket.NED_REQ_GET_DEVICE_INFO)
            .packet(payload)
    }

    fun packetForPlainData(): ByteArray {
        val reserved:Short = 0
        val payloadSize = 2
        val payload = ByteArray(payloadSize).apply {
            ByteBuffer.wrap(this)
                .putShort(reserved)
        }

        return NedPacket(NedPacket.NED_REQ_GET_PLAIN_DATA)
            .packet(payload)
    }


}

