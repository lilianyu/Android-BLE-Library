package nedprotocol

object NedHandler {

    fun handle(req:NedPacket): ByteArray? {

        return when(req.commandCode) {
            NedPacket.NED_REQ_UPGRADE -> MockNedResponse.respPacketForUpgradeInfo()
            NedPacket.NED_REQ_GET_DEVICE_INFO -> MockNedResponse.respPacketForDeviceInfo()
            NedPacket.NED_REQ_SEND_UPGRADE_PACKAGE -> MockNedResponse.respPacketForUpgradePackage()
            NedPacket.NED_REQ_GET_PLAIN_DATA -> MockNedResponse.respPacketForPlainData()
            else -> null
        }

    }
}