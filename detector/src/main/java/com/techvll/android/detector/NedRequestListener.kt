package com.techvll.android.detector

import android.bluetooth.BluetoothDevice
import nedprotocol.NedPacket

abstract class NedRequestListener {
    open fun onStart(device: BluetoothDevice) {}

    abstract fun onCompleted(device: BluetoothDevice, packet: NedPacket?)

    abstract fun onFail(device: BluetoothDevice, failInfo: FailInfo, packet: NedPacket?)

    open fun onProgress(device: BluetoothDevice, packet:ByteArray, soFar:Int, totalSize:Int) {}

    open fun onTimeout(device: BluetoothDevice) {}
}