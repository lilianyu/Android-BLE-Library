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
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ble_gatt_client.repository.ScannerRepository
import spec.NedServiceProfile
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

    private val defaultScope = CoroutineScope(Dispatchers.Default)

    private lateinit var bluetoothObserver: BroadcastReceiver

    private var statusChangedChannel: SendChannel<String>? = null
    private var responseChannel: SendChannel<ByteArray>? = null


    private var deviceListener: ((BluetoothDevice?) -> Unit)? = null

    private val clientManagers = mutableMapOf<String, ClientManager>()

    private lateinit var scannerRepo: ScannerRepository


    override fun onCreate() {
        super.onCreate()

        // Setup as a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(
                        GattService::class.java.simpleName,
                        resources.getString(R.string.gatt_service_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                )

            val notificationService =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.createNotificationChannel(notificationChannel)
        }

        val notification = NotificationCompat.Builder(this, GattService::class.java.simpleName)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(resources.getString(R.string.gatt_service_name))
                .setContentText(resources.getString(R.string.gatt_service_running_notification))
                .setAutoCancel(true)

        startForeground(1, notification.build())

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
                            BluetoothDevice.BOND_BONDED -> addDevice(device)
                            BluetoothDevice.BOND_NONE -> removeDevice(device)
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
                    responseChannel = null
                    deviceListener = null
                    true
                }
                else -> false
            }

    /**
     * A binding to be used to interact with data of the service
     */
    inner class DataPlane : Binder() {
        fun setChannel(statusChannel: SendChannel<String>, respChannel: SendChannel<ByteArray>) {
            statusChangedChannel = statusChannel
            responseChannel = respChannel
        }

        fun setOnDevicesChangeListener(listener: (BluetoothDevice?) -> Unit) {
            deviceListener = listener
        }

        fun connectDevice(device: BluetoothDevice, connectionObserver: ConnectionObserver) {
            val clientManager = clientManagers[device.address]
            clientManager?.connect(device)?.useAutoConnect(true)?.enqueue()
            clientManager?.connectionObserver = connectionObserver
        }

        fun enableServices() {
            // Startup BLE if we have it
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager.adapter?.isEnabled == true) {
                enableBleServices()
            }
        }

        fun write(device: BluetoothDevice, data: ByteArray) {
            val clientManager = clientManagers[device.address]
            clientManager?.write(data)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBleServices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter?.isEnabled == true) {
            Log.i(TAG, "Enabling BLE services")

            if (clientManagers.isEmpty()) {
                defaultScope.launch {
                    scannerRepo = ScannerRepository(this@GattService, bluetoothManager.adapter)
                    statusChangedChannel?.send("正在扫描设备...")
                    val device = scannerRepo.searchForServer()
                    addDevice(device)
                    deviceListener?.invoke(device)
                }
            }
        } else {
            defaultScope.launch {
                statusChangedChannel?.send("请打开蓝牙开关...")
            }
            Log.w(TAG, "Cannot enable BLE services as either there is no Bluetooth adapter or it is disabled")
        }
    }

    private fun disableBleServices() {
        clientManagers.values.forEach { clientManager ->
            clientManager.close()
        }
        clientManagers.clear()

        deviceListener?.invoke(null)
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        if (!clientManagers.containsKey(device.address)) {
            val clientManager = ClientManager()
            clientManagers[device.address] = clientManager
        }
    }

    private fun removeDevice(device: BluetoothDevice) {
        clientManagers.remove(device.address)?.close()
    }

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

    private inner class ClientManager : BleManager(this@GattService) {
        private var eventCharacteristic: BluetoothGattCharacteristic? = null
        private var dataCharacteristic: BluetoothGattCharacteristic? = null
        override fun getGattCallback(): BleManagerGattCallback = GattCallback()

        override fun log(priority: Int, message: String) {
            if (BuildConfig.DEBUG || priority == Log.ERROR) {
                Log.println(priority, TAG, message)
            }
        }

        fun write(data: ByteArray) {
            writeCharacteristic(dataCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .split()
                .enqueue()
        }

        private inner class GattCallback : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(NedServiceProfile.NED_EVENT_SERVICE_UUID)
                eventCharacteristic =
                        service?.getCharacteristic(NedServiceProfile.NED_EVENT_CHARACTERISTIC_UUID)
                val eventCharacteristicProperties = eventCharacteristic?.properties ?: 0

                val dataService = gatt.getService(NedServiceProfile.NED_DATA_SERVICE_UUID)
                dataCharacteristic =
                    dataService?.getCharacteristic(NedServiceProfile.NED_DATA_CHARACTERISTIC_UUID)
                val dataCharacteristicProperties = dataCharacteristic?.properties ?: 0

                return (eventCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) &&
                        (dataCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            }

            override fun initialize() {
                var mtu = 512
                requestMtu(mtu)
                    .with { bluetoothDevice: BluetoothDevice, i: Int ->
                        mtu = i
                    }
                    .enqueue()

                setNotificationCallback(eventCharacteristic)
                    .merge {
                            output, lastPacket, index ->
                        output.write(lastPacket)
                        lastPacket?.size != mtu
                    }
                    .with { _, data ->
                        if (data.value != null) {
                            defaultScope.launch {
                                responseChannel?.send(data.value!!)
                            }
                        }
                }

                beginAtomicRequestQueue()
                        .add(enableNotifications(eventCharacteristic)
                                .fail { _: BluetoothDevice?, status: Int ->
                                    log(Log.ERROR, "Could not subscribe: $status")
                                    disconnect().enqueue()
                                }
                        )
                        .done {
                            log(Log.INFO, "Target initialized")
                        }
                        .enqueue()
            }

            override fun onServicesInvalidated() {
                eventCharacteristic = null
            }
        }
    }
}
