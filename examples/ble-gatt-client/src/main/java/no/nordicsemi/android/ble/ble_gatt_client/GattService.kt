/*
 * Copyright (c) 2020, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.ble.ble_gatt_client

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import nedprotocol.NedPacket
import no.nordicsemi.android.ble.observer.ConnectionObserver


/**
 * Connects with a Bluetooth LE GATT service and takes care of its notifications. The service
 * runs as a foreground service, which is generally required so that it can run even
 * while the containing app has no UI. It is also possible to have the service
 * started up as part of the OS boot sequence using code similar to the following:
 *
 * <pre>
 *     class OsNotificationReceiver : BroadcastReceiver() {
 *          override fun onReceive(context: Context?, intent: Intent?) {
 *              when (intent?.action) {
 *                  // Start our Gatt service as a result of the system booting up
 *                  Intent.ACTION_BOOT_COMPLETED -> {
 *                     context?.startForegroundService(Intent(context, GattService::class.java))
 *                  }
 *              }
 *          }
 *      }
 * </pre>
 */
class GattService : Service() {

    /*
     * Manages the entire GATT service, declaring the services and characteristics on offer
     */
    companion object {
        /**
         * A binding action to return a binding that can be used in relation to the service's data
         */
        const val DATA_PLANE_ACTION = "data-plane"

        private const val TAG = "gatt-serice"
    }

    private val defaultScope = CoroutineScope(Dispatchers.Default)

    private lateinit var bluetoothObserver: BroadcastReceiver

    private var statusChangedChannel: SendChannel<String>? = null

    private var deviceListener: ((BluetoothDevice?) -> Unit)? = null

//    private val clientManagers = mutableMapOf<String, ClientManager>()

    private val nedClient = NedClient(this)

//    private lateinit var scannerRepo: ScannerRepository


    override fun onCreate() {
        super.onCreate()

        // Observe OS state changes in BLE
        bluetoothObserver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val bluetoothState = intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                -1
                        )
                        when (bluetoothState) {
                            BluetoothAdapter.STATE_ON -> enableBleServices()
                            BluetoothAdapter.STATE_OFF -> disableBleServices()
                        }
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device =
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        Log.d(TAG, "Bond state changed for device ${device?.address}: ${device?.bondState}")
                        when (device?.bondState) {
                            BluetoothDevice.BOND_BONDED -> nedClient.addDevice(device)
                            BluetoothDevice.BOND_NONE -> nedClient.removeDevice(device)
                        }
                    }
                }
            }
        }
        registerReceiver(bluetoothObserver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(bluetoothObserver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothObserver)
        disableBleServices()
    }

    override fun onBind(intent: Intent?): IBinder? =
            when (intent?.action) {
                DATA_PLANE_ACTION -> {
                    DataPlane()
                }
                else -> null
            }

    override fun onUnbind(intent: Intent?): Boolean =
            when (intent?.action) {
                DATA_PLANE_ACTION -> {
                    statusChangedChannel = null
                    deviceListener = null
                    true
                }
                else -> false
            }

    /**
     * A binding to be used to interact with data of the service
     */
    inner class DataPlane : Binder() {
        fun setChannel(statusChannel: SendChannel<String>) {
            statusChangedChannel = statusChannel
        }

        fun setOnDevicesChangeListener(listener: (BluetoothDevice?) -> Unit) {
            deviceListener = listener
        }

        fun connectDevice(device: BluetoothDevice, connectionObserver: ConnectionObserver) {
            nedClient.connectDevice(device, connectionObserver)
        }

        fun disconnectDevice(device: BluetoothDevice) {
            nedClient.disconnectDevice(device)
        }

        fun enableServices() {
            // Startup BLE if we have it
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager.adapter?.isEnabled == true) {
                enableBleServices()
            }
        }

        fun getDeviceInfo(device: BluetoothDevice, listener: NedRequestListener): NedRequest? {
            return nedClient.getDeviceInfo(device, listener)
        }

        fun getPlainData(device: BluetoothDevice, listener: NedRequestListener): NedRequest?  {
            return nedClient.getPlainData(device, listener)
        }

        fun upgradePackage(device: BluetoothDevice, pkg: ByteArray, newVersion:Int, listener: NedRequestListener): NedRequest?  {
            return nedClient.upgradeDevice(device, listener, pkg, newVersion)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBleServices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter?.isEnabled == true) {
            Log.i(TAG, "Enabling BLE services")

//            if (clientManagers.isEmpty()) {
//                defaultScope.launch {
//                    scannerRepo = ScannerRepository(this@GattService, bluetoothManager.adapter)
//                    statusChangedChannel?.send("正在扫描设备...")
//                    val device = scannerRepo.searchForServer()
//                    addDevice(device)
//                    deviceListener?.invoke(device)
//                }
//            }
        } else {
            defaultScope.launch {
                statusChangedChannel?.send("请打开蓝牙开关...")
            }
            Log.w(TAG, "Cannot enable BLE services as either there is no Bluetooth adapter or it is disabled")
        }
    }

    private fun disableBleServices() {
        nedClient.close()

        deviceListener?.invoke(null)
    }
}
