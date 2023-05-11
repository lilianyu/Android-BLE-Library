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
import android.annotation.TargetApi
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.elvishew.xlog.XLog
import kotlinx.coroutines.*
import nedprotocol.NedPacket
import no.nordicsemi.android.ble.ble_gatt_client.databinding.ActivityMainBinding
import no.nordicsemi.android.ble.ble_gatt_client.repository.ScannerRepository
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: Int = 1001

    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val viewModel by viewModels<UserViewModel>()

    private var gattServiceConn: MainActivity.GattServiceConn? = null

    private var gattServiceData: GattService.DataPlane? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            XLog.i("callbackType = $callbackType")
            result?.let {

                val deviceItem = adapter.deviceList.find { item ->
                    item.device.address == it.device.address
                }

                binding.swipeRefresh.isRefreshing = false

                if (deviceItem == null) {

                    adapter.deviceList.add(DeviceAdapter.DeviceAdapterItem(it.device))
                    adapter.notifyDataSetChanged()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (errorCode != SCAN_FAILED_ALREADY_STARTED) {
                binding.swipeRefresh.isRefreshing = false
            }

            XLog.e("Scan Failed with $errorCode")
//                binding.textViewGattCharacteristicValue.text = "扫描失败"
        }
    }

    private var adapter: DeviceAdapter = DeviceAdapter().apply {
        connectListener = {
//            var intent = Intent(this@MainActivity, DeviceManagerActivity::class.java)
//            intent.putExtra("device", it)
//            startActivity(intent)

            if (it != null) {
                gattServiceData?.connectDevice(it, DeviceConnectionCallback())
            }
        }

        deviceInfoListener = {
            gattServiceData?.getDeviceInfo(it)
                ?.apply {

                    fail { failInfo: FailInfo, nedPacket: NedPacket? ->
                        XLog.i("Fail ${failInfo.message}")

                        mainScope.launch {
                            var errorMessage = "${failInfo.message}"
                            failInfo.extra?.let {extra ->
                                errorMessage = "${errorMessage}, $extra"
                            }
                            nedPacket?.let { nedPacket ->
                                errorMessage = "${errorMessage} - ${nedPacket.packet?.map {byte ->  "%02X".format(byte) }.toString()}"
                            }

//                            binding.respData.text = errorMessage
                        }
                    }
                    done { nedPacket ->
                        mainScope.launch {
                            val deviceItem = deviceList.find { item ->
                                item.device.address == it.address
                            }

                            deviceItem?.hardwareVersion = nedPacket?.payload?.let { payload ->
                                XLog.i(payload)
                                ByteBuffer.wrap(payload).getInt(0).toUInt()
                            }

                            deviceItem?.softwareVersion = nedPacket?.payload?.let { payload ->
                                XLog.i(payload)
                                ByteBuffer.wrap(payload).getInt(4).toUInt()
                            }

                            notifyDataSetChanged()

//                            binding.respData.text = hexBytes.toString()
                        }
                    }
                }?.enqueue()
        }
    }

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bondedDevice.adapter = adapter

//        if (bluetoothManager.adapter?.bondedDevices?.toMutableList() != null) {
//            adapter.deviceList = bluetoothManager.adapter?.bondedDevices?.toMutableList()!!
//        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val repository = ScannerRepository(this@MainActivity, bluetoothManager.adapter)

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            stuffMarshMallow();
        }

        binding.swipeRefresh.setOnRefreshListener {
            scanDevices()
        }

        scanDevices()


        // Startup our Bluetooth GATT service explicitly so it continues to run even if
        // this activity is not in focus
        startService(Intent(this, GattService::class.java))

//        viewModel.getUser(1)
    }

    private fun scanDevices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val repository = ScannerRepository(this@MainActivity, bluetoothManager.adapter)

        with(mainHandler) {
//            binding.textViewGattCharacteristicValue.text = "开始扫描设备..."
            repository.startScan(callback)
            binding.scanDevices.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
            postDelayed({
//                binding.textViewGattCharacteristicValue.text = "扫描完成"

                repository.stopScan(callback)

                if (adapter.deviceList.isNotEmpty()) {
                    binding.scanDevices.visibility = View.GONE
                    binding.swipeRefresh.visibility = View.VISIBLE
                    binding.swipeRefresh.isRefreshing = false
                } else {
                    binding.scanStatus.text = "重新扫描"
                    binding.scanStatus.isClickable = true
                    binding.scanView.stopScan()
                    binding.scanStatus.setOnClickListener() { view ->
                        scanDevices()
                        binding.scanView.startScan()
                        binding.scanStatus.isClickable = false
                        binding.scanStatus.text = "正在扫描设备..."
                    }
                }


            }, 5000)
        }
    }
    override fun onStart() {
        super.onStart()

        val latestGattServiceConn = GattServiceConn()
        if (bindService(Intent(GattService.DATA_PLANE_ACTION, null, this, GattService::class.java), latestGattServiceConn, 0)) {
            gattServiceConn = latestGattServiceConn
        }
    }

    override fun onStop() {
        super.onStop()

        if (gattServiceConn != null) {
            unbindService(gattServiceConn!!)
            gattServiceConn = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // We only want the service around for as long as our app is being run on the device
        stopService(Intent(this, GattService::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.menu_main_sharing -> {
                try {
                    externalCacheDir?.let {
                        val name = android.text.format.DateFormat.format("yyyy-MM-dd", Date())
                        val  file = File("${it.absolutePath}/${name}")

                        val contentUri = FileProvider.getUriForFile(this, NedApplication.FILE_PROVIDER_AUTHORITY, file)

                        if (contentUri != null) {
                            var shareIntent = Intent(Intent.ACTION_SEND)
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            shareIntent.type = "text/plain"
                            /** set the corresponding mime type of the file to be shared */
                            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)

                            startActivity(Intent.createChooser(shareIntent, "Share to"))
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when (requestCode) {
            REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS -> {
                val perms: MutableMap<String, Int> = HashMap()
                // Initial
                perms[android.Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED


                // Fill with results
                var i = 0
                while (i < permissions.size) {
                    perms[permissions[i]] = grantResults[i]
                    i++
                }

                // Check for ACCESS_FINE_LOCATION
                if (perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted

                    scanDevices()
                } else {
                    // Permission Denied
                    Toast.makeText(
                        this,
                        "One or More Permissions are DENIED Exiting App :(",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    finish()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun stuffMarshMallow() {
        val permissionsNeeded: MutableList<String> = ArrayList()
        val permissionsList: MutableList<String> = ArrayList()
        if (!addPermission(
                permissionsList,
                android.Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location")

        if (!addPermission(
                permissionsList,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
            permissionsNeeded.add("写权限")


        if (permissionsList.size > 0) {
            if (permissionsNeeded.size > 0) {

                // Need Rationale
                var message = "App need access to " + permissionsNeeded[0]
                for (i in 1 until permissionsNeeded.size) message =
                    message + ", " + permissionsNeeded[i]
                showMessageOKCancel(
                    message
                ) { dialog, which ->
                    requestPermissions(
                        permissionsList.toTypedArray(),
                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
                    )
                }
                return
            }
            requestPermissions(
                permissionsList.toTypedArray(),
                REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
            )
            return
        }
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun addPermission(permissionsList: MutableList<String>, permission: String): Boolean {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission)
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission)) return false
        }
        return true
    }

    private inner class GattServiceConn : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            if (BuildConfig.DEBUG && GattService::class.java.name != name?.className) {
                error("Disconnected from unknown service")
            } else {
                gattServiceData = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (BuildConfig.DEBUG && GattService::class.java.name != name?.className)
                error("Connected to unknown service")
            else {
                gattServiceData = service as GattService.DataPlane
                XLog.i("onServiceConnected, gattServiceData = $gattServiceData")

//                if (device != null) {
//                    gattServiceData?.connectDevice(device!!, DeviceConnectionCallback())
//                }

//                gattServiceData?.setOnDevicesChangeListener {
//                    if (it != null) {
//                        gattServiceData?.connectDevice(it!!, DeviceConnectionCallback())
//                    }
//                }
            }

            gattServiceData?.enableServices()
        }
    }

    inner class DeviceConnectionCallback : ConnectionObserver {

        override fun onDeviceConnecting(device: BluetoothDevice) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.Connecting
            adapter.notifyDataSetChanged()
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.Connected
            adapter.notifyDataSetChanged()
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.FailedToConnect
            adapter.notifyDataSetChanged()
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.Ready
            adapter.notifyDataSetChanged()

            XLog.i("gattServiceData = $gattServiceData")

//            gattServiceData?.getDeviceInfo(device)
//                ?.apply {
//
//                    fail { failInfo: FailInfo, nedPacket: NedPacket? ->
//                        XLog.i("Fail ${failInfo.message}")
//
//                        mainScope.launch {
//                            var errorMessage = "${failInfo.message}"
//                            failInfo.extra?.let {extra ->
//                                errorMessage = "${errorMessage}, $extra"
//                            }
//                            nedPacket?.let { nedPacket ->
//                                errorMessage = "${errorMessage} - ${nedPacket.packet?.map {byte ->  "%02X".format(byte) }.toString()}"
//                            }
//
////                            binding.respData.text = errorMessage
//                        }
//                    }
//                    done {
//                        mainScope.launch {
//                            deviceItem?.hardwareVersion = it?.payload?.let { payload ->
//                                XLog.i(payload)
//                                ByteBuffer.wrap(payload).getInt(0).toUInt()
//                            }
//
//                            deviceItem?.softwareVersion = it?.payload?.let { payload ->
//                                XLog.i(payload)
//                                ByteBuffer.wrap(payload).getInt(4).toUInt()
//                            }
//
//                            adapter.notifyDataSetChanged()
//
////                            binding.respData.text = hexBytes.toString()
//                        }
//                    }
//                }?.enqueue()
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.Disconnecting
            adapter.notifyDataSetChanged()
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            val deviceItem = adapter.deviceList.find { item ->
                item.device.address == device.address
            }

            deviceItem?.connectStatus = ConnectionStatus.Disconnected
            adapter.notifyDataSetChanged()
        }
    }

}

