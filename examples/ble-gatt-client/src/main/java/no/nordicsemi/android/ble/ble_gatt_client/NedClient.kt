package no.nordicsemi.android.ble.ble_gatt_client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import no.nordicsemi.android.ble.observer.ConnectionObserver

class NedClient(val context:Context) {

    val bleDevices = mutableMapOf<String, BleDevice>()

    fun connectDevice(device: BluetoothDevice, connectionObserver: ConnectionObserver) {
        addDevice(device)

        val bleDevice = bleDevices[device.address]
        bleDevice?.connect(device)?.useAutoConnect(true)?.enqueue()
        bleDevice?.connectionObserver = connectionObserver
    }

    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice) {
        if (!bleDevices.containsKey(device.address)) {
            val clientManager = BleDevice(context)
            bleDevices[device.address] = clientManager
        }
    }

    fun removeDevice(device: BluetoothDevice) {
        bleDevices.remove(device.address)?.close()
    }

    fun close() {
        bleDevices.values.forEach { clientManager ->
            clientManager.close()
        }
        bleDevices.clear()
    }

}