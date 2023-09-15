package no.nordicsemi.android.ble.ble_gatt_client.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import spec.NedServiceProfile.NED_DATA_SERVICE_UUID
import spec.NedServiceProfile.manufacturerData
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
class ScannerRepository  constructor(
    val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
) {
    private val bluetoothLeScanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
            ?: throw NullPointerException("Bluetooth not initialized")
    }

    fun startScan(callback: ScanCallback) {
        val scanSettings = ScanSettings.Builder()
            .setReportDelay(0) // Set to 0 to be notified of scan results immediately.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val manufacturerDataMask = ByteArray(14)

        val scanFilters = listOf(
            ScanFilter.Builder()
//                .setManufacturerData(0x5253, manufacturerData)
                .setManufacturerData(0x5352, manufacturerData, manufacturerDataMask)
//                .setServiceUuid(ParcelUuid(NED_DATA_SERVICE_UUID))
                .build()
        )

        bluetoothLeScanner.startScan(
            scanFilters,
            scanSettings,
            callback
        )
    }

    fun stopScan(callback: ScanCallback) {
        bluetoothLeScanner.stopScan(callback)
    }
}
