package spec

import no.nordicsemi.android.ble.data.DataMerger
import no.nordicsemi.android.ble.data.DataStream

class PacketMerger: DataMerger {
    override fun merge(output: DataStream, lastPacket: ByteArray?, index: Int): Boolean {
        TODO("Not yet implemented")
    }
}