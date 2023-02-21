package no.nordicsemi.android.ble.ble_gatt_client.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import spec.NedServiceProfile.NED_DATA_SERVICE_UUID
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

    /**
     * Starts scanning for an advertising server.
     * Returns the first found device.
     */
    suspend fun searchForServer(): BluetoothDevice = suspendCancellableCoroutine { continuation ->

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result
                    ?.let {
                        if (continuation.isActive) {
                            continuation.resume(it.device)
                        }
                    }
                    .also { bluetoothLeScanner.stopScan(this) }
            }

            override fun onScanFailed(errorCode: Int) {
                continuation.resumeWithException(ScanningException(errorCode))
            }
        }
        continuation.invokeOnCancellation {
            bluetoothLeScanner.stopScan(callback)
        }

        val scanSettings = ScanSettings.Builder()
            .setReportDelay(0) // Set to 0 to be notified of scan results immediately.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(NED_DATA_SERVICE_UUID))
                .build()
        )

        bluetoothLeScanner.startScan(
            scanFilters,
            scanSettings,
            callback
        )
    }

}
