package com.techvll.android.detector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nedprotocol.NedPacket
import no.nordicsemi.android.ble.observer.ConnectionObserver

class NedClient(val context:Context) {

    val bleDevices = mutableMapOf<String, BleDevice>()

    fun connectDevice(device: BluetoothDevice, connectionObserver: ConnectionObserver) {
        addDevice(device)

        val bleDevice = bleDevices[device.address]
        bleDevice?.connect(device)?.useAutoConnect(true)?.enqueue()
        bleDevice?.connectionObserver = connectionObserver
    }

    fun disconnectDevice(device: BluetoothDevice) {
        val bleDevice = bleDevices[device.address]
        bleDevice?.disconnect()?.enqueue()
    }

    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice) {
        if (!bleDevices.containsKey(device.address)) {
            val clientManager = BleDevice(context, device)
            bleDevices[device.address] = clientManager
        }
    }

    fun removeDevice(device: BluetoothDevice) {
        bleDevices.remove(device.address)?.close()
    }

    fun getDeviceInfo(device: BluetoothDevice, listener: NedRequestListener): NedRequest? {
        if (isDeviceReady(device, listener)) {
            val bleDevice = bleDevices[device.address]
            bleDevice?.let {
                val nedRequest = NedRequest.nedDeviceInfoRequest().setBleDevice(bleDevice)
                if (listener != null) {
                    nedRequest.addListeners(listener)
                }
                return nedRequest
            }
        }

        return null
    }

    fun getPlainData(device: BluetoothDevice, listener: NedRequestListener): NedRequest?  {
        if (isDeviceReady(device, listener)) {
            val bleDevice = bleDevices[device.address]

            bleDevice?.let {
                val nedRequest = NedRequest.nedPlainDataRequest().setBleDevice(bleDevice)

                if (listener != null) {
                    nedRequest.addListeners(listener)
                }

                return nedRequest
            }
        }

        return null
    }

    fun upgradeDevice(device: BluetoothDevice, listener: NedRequestListener, pkg: ByteArray, newVersion: Int): NedRequest? {
        if (isDeviceReady(device, listener)) {
            val bleDevice = bleDevices[device.address]

            bleDevice?.let {
                val nedRequest = NedRequest.nedUpgradeRequest(pkg, newVersion).setBleDevice(bleDevice)

                if (listener != null) {
                    nedRequest.addListeners(listener)
                }

                nedRequest.addListeners(object : NedRequestListener() {
                    val notificationBuilder = NotificationCompat.Builder(context, GattService::class.java.simpleName)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(context.getString(R.string.gatt_service_name))
                        .setContentText(context.getString(R.string.gatt_service_running_notification))
                        .setAutoCancel(true)

                    val notificationManager = NotificationManagerCompat.from(context)

                    override fun onStart(device: BluetoothDevice) {
                        super.onStart(device)

                        // Setup as a foreground service
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val notificationChannel =
                                NotificationChannel(
                                    GattService::class.java.simpleName,
                                    context.getString(R.string.gatt_service_name),
                                    NotificationManager.IMPORTANCE_DEFAULT
                                )

                            val notificationService =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationService.createNotificationChannel(notificationChannel)
                        }

                        notificationBuilder.setProgress(100, 0, false)
                        val notification = notificationBuilder.build()
                        notification.flags = Notification.FLAG_FOREGROUND_SERVICE
                        (context as Service).startForeground(1, notification)
                    }

                    override fun onCompleted(device: BluetoothDevice, packet: NedPacket?) {
                        notificationBuilder
                            .setContentText("升级已完成")

                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }

                        notificationManager.notify(1, notificationBuilder.build());

                        (context as Service).stopForeground(Service.STOP_FOREGROUND_DETACH)
                    }

                    override fun onFail(
                        device: BluetoothDevice,
                        failInfo: FailInfo,
                        packet: NedPacket?
                    ) {
                    }

                    override fun onProgress(
                        device: BluetoothDevice,
                        packet: ByteArray,
                        soFar: Int,
                        totalSize: Int
                    ) {
                        val percentage = soFar*100 / totalSize
                        notificationBuilder
                            .setProgress(100, percentage, false)
                            .setContentText("${context.getString(R.string.gatt_service_running_notification)}：${percentage}%")
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        notificationManager.notify(1, notificationBuilder.build());
                    }

                    override fun onTimeout(device: BluetoothDevice) {
                    }

                })

                return nedRequest
            }
        }

        return null
    }

    private fun isDeviceReady(device: BluetoothDevice, listener: NedRequestListener):Boolean {
        val bleDevice = bleDevices[device.address]
        if (bleDevice == null || !bleDevice.isConnected) {
            listener.onFail(device, FailInfo.FailDeviceDisconnected, null)
            return false
        }

        if (bleDevice.isRequestOngoing) {
            listener.onFail(device, FailInfo.FailDeviceBusy, null)
            return false
        }

        return true
    }

    fun close() {
        bleDevices.values.forEach { clientManager ->
            clientManager.close()
        }
        bleDevices.clear()
    }

}