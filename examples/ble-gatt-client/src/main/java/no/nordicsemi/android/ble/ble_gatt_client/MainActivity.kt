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
import android.net.Uri
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.elvishew.xlog.XLog
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import kotlinx.coroutines.*
import nedprotocol.NedPacket
import no.nordicsemi.android.ble.ble_gatt_client.databinding.ActivityMainBinding
import no.nordicsemi.android.ble.ble_gatt_client.repository.ScannerRepository
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*


class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: Int = 1001

    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val viewModel by viewModels<UserViewModel>()

    private var scanPending = false;

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            XLog.i("callbackType = $callbackType")
            result?.let {

                val deviceItem = adapter.deviceList.find { item ->
                    item.device.address == it.device.address
                }

                binding.scanDevices.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
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
        connectListener = {item, cmd ->
            if (cmd == DeviceAdapter.CMD_CONNECT ) {
                GattServiceClient.gattServiceProxy?.connectDevice(item.device, DeviceConnectionCallback())
            } else {
                GattServiceClient.gattServiceProxy?.disconnectDevice(item.device)
            }
        }

        deviceInfoListener = {
            GattServiceClient.gattServiceProxy?.getDeviceInfo(it, object:NedRequestListener(){
                override fun onCompleted(device: BluetoothDevice, nedPacket: NedPacket?) {
                    viewModel.currentDeviceToUpgrade = null

                    mainScope.launch {
                        val deviceItem = deviceList.find { item ->
                            item.device.address == device.address
                        }

                        deviceItem?.hardwareVersion = nedPacket?.payload?.let { payload ->
                            XLog.i(payload)
                            ByteBuffer.wrap(payload).getInt(0).toUInt()
                        }

                        deviceItem?.softwareVersion = nedPacket?.payload?.let { payload ->
                            XLog.i(payload)
                            ByteBuffer.wrap(payload).getInt(4).toUInt()
                        }

                        deviceItem?.address = nedPacket?.payload?.copyOfRange(8,24)

                        notifyDataSetChanged()

//                            binding.respData.text = hexBytes.toString()
                    }
                }

                override fun onFail(device: BluetoothDevice, failInfo: FailInfo, nedPacket: NedPacket?) {
                    XLog.i("Fail ${failInfo.message}")
                    viewModel.currentDeviceToUpgrade = null

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

            })?.enqueue()
        }

        upgradeListener = {
            if (it.connectStatus == ConnectionStatus.Ready) {
                var intent = Intent(this@MainActivity, DeviceManagerActivity::class.java)
                intent.putExtra("device", it.device)
                startActivity(intent)
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("请先连接设备再使用")
                    .setPositiveButton("OK", null)
                    .create()
                    .show()
            }
        }

        checkNewVersion = { item ->

            item.hardwareVersion?.let { hwVersion ->
                item.softwareVersion?.let { swVersion ->
                    viewModel.checkNewVersion(item,
                        hwVersion.toLong(), swVersion.toLong())
                }
            }
        }
    }

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bondedDevice.adapter = adapter

        binding.contact.text = "商务垂询：${binding.contact.text}"

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val repository = ScannerRepository(this@MainActivity, bluetoothManager.adapter)

        binding.swipeRefresh.setOnRefreshListener {
            scanDevices()
        }

        scanDevices()

        binding.contact.setOnClickListener {
            val number = (it as TextView).text.split(":").last().trim()
            val uri = "tel:${number}"
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse(uri)
            startActivity(intent)
        }

        viewModel.packageInfo.observe(this) { packageInfo ->
            if (packageInfo == null) {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("${viewModel.newVersionError}")
                    .setPositiveButton("OK", null)
                    .create()
                    .show()

                return@observe
            }

            val path = Uri.parse(packageInfo.url).lastPathSegment

            val cacheFile = File(cacheDir, "packages/$path")

            val uri: Uri = FileProvider.getUriForFile(this, applicationContext.packageName + ".FileProvider", cacheFile)

            FileDownloader.getImpl().create(packageInfo.url)
                .setPath(cacheFile.absolutePath)
                .setListener (object : FileDownloadListener() {
                    override fun pending(
                        task: BaseDownloadTask?,
                        soFarBytes: Int,
                        totalBytes: Int
                    ) {
                        XLog.i("pending")
                    }

                    override fun progress(
                        task: BaseDownloadTask?,
                        soFarBytes: Int,
                        totalBytes: Int
                    ) {
                        XLog.i("progress")
                    }

                    override fun completed(task: BaseDownloadTask?) {
                        XLog.i("completed")

                        var newVersion: UInt? = packageInfo.versionCode?.toUInt()
                        val ver = "${newVersion?.shr(24)?.toUByte()}.${newVersion?.shr(16)?.toUByte()}.${newVersion?.shr(8)?.toUByte()}.${newVersion?.toUByte()}"

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("版本升级")
                            .setMessage("有新的版本可用：${ver} \n ${packageInfo.description}")
                            .setPositiveButton("升级" ) { dialogInterface: DialogInterface, i: Int ->

                                val bytes = FileInputStream(cacheFile)
                                    .use {
                                        val fileBytes = ByteArray(it.available())
                                        it.read(fileBytes)
                                        fileBytes
                                    }

                                viewModel.currentDeviceToUpgrade?.let { device ->
                                    GattServiceClient.gattServiceProxy?.upgradePackage(device, bytes, packageInfo.versionCode!!.toInt(), object : NedRequestListener() {
                                        override fun onCompleted(
                                            device: BluetoothDevice,
                                            packet: NedPacket?
                                        ) {
                                            val deviceItem = adapter.deviceList.find { item ->
                                                item.device.address == device.address
                                            }

                                            mainScope.launch {
//                                                binding.sendUpgradePackage.isEnabled = true
//                                                binding.respUpgradePackage.text = "发送完成"
                                                XLog.d("activity: finish")

                                                deviceItem?.stUpgrade = 2
                                                deviceItem?.upgradeProgress = 100u

                                                adapter.notifyDataSetChanged()
                                            }

                                            viewModel.currentDeviceToUpgrade = null
                                        }

                                        override fun onFail(
                                            device: BluetoothDevice,
                                            failInfo: FailInfo,
                                            nedPacket: NedPacket?
                                        ) {
                                            mainScope.launch {
                                                var errorMessage = "${failInfo.message}"
                                                failInfo.extra?.let {extra ->
                                                    errorMessage = "${errorMessage}, $extra"
                                                }
                                                nedPacket?.let { nedPacket ->
                                                    errorMessage = "$errorMessage - ${nedPacket.packet?.map { byte ->  "%02X".format(byte) }.toString()}"
                                                }

                                                XLog.e("activity: ${failInfo.message}")

                                                val deviceItem = adapter.deviceList.find { item ->
                                                    item.device.address == device.address
                                                }



                                                viewModel.currentDeviceToUpgrade = null
                                            }
                                        }

                                        override fun onProgress(
                                            device: BluetoothDevice,
                                            packet:ByteArray,
                                            soFar:Int,
                                            totalSize:Int) {
                                            val deviceItem = adapter.deviceList.find { item ->
                                                item.device.address == device.address
                                            }

                                            mainScope.launch {
//                            val packetString = packet?.map { "%02X".format(it) }.toString()
//                                                binding.respUpgradePackage.text = "已发送${index}包数据"
                                                XLog.d("activity: 已发送${soFar}包数据")

                                                deviceItem?.stUpgrade = 1

                                                val percentage = soFar*100 / totalSize
                                                deviceItem?.upgradeProgress = percentage.toUInt()

                                                adapter.notifyDataSetChanged()
                                            }
                                        }

                                    })?.enqueue()
                                }
                            }
                            .setNegativeButton("退出", null)
                            .create()
                            .show()
                    }

                    override fun paused(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) {
                        XLog.i("paused")
                    }

                    override fun error(task: BaseDownloadTask?, e: Throwable?) {
                        XLog.i("error")
                    }

                    override fun warn(task: BaseDownloadTask?) {
                        XLog.i("warn")
                    }

                }).start()
        }
    }

    private fun scanDevices() {

        val checkAllNecessaryPermissions = checkAllNecessaryPermissions()
        //权限不够，申请权限
        if (checkAllNecessaryPermissions.isNotEmpty()) {
            stuffMarshMallow()
            scanPending = true
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter.bluetoothLeScanner == null) {
            AlertDialog.Builder(this@MainActivity)
                .setMessage("请您先打开蓝牙再使用，感谢您的支持")
                .setPositiveButton("OK") { dialogInterface: DialogInterface, i: Int ->
                    stopScan()
                }
                .create()
                .show()

            return
        }

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
                    stopScan()
                }


            }, 60000)
        }
    }



    override fun onStart() {
        super.onStart()

        if (!GattServiceClient.isConnected()) {
            GattServiceClient.bindService(applicationContext)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.menu_about -> {
                val intent = Intent(this, AboutActivity::class.java)
                startActivity(intent)
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
                if (checkAllNecessaryPermissions().isEmpty()) {
                    // All Permissions Granted
                    if (scanPending) {
                        scanPending = false
                        scanDevices()
                    }
                } else {
                    // Permission Denied
                    Toast.makeText(
                        this,
                        "One or More Permissions are DENIED Exiting App :(",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        }
    }

    private fun stopScan() {
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

    @TargetApi(Build.VERSION_CODES.M)
    private fun stuffMarshMallow() {
        val permissionsNeeded: MutableList<String> = ArrayList()
        val permissionsList: MutableList<String> = ArrayList()

        for (permission in BTConstants.scanPermissions) {
            if (!addPermission(
                    permissionsList,
                    permission)) {
                BTConstants.permissionsRationals[permission]?.let {
                    permissionsNeeded.add(
                        it
                    )
                }
            }
        }

        if (permissionsList.size > 0) {
            if (permissionsNeeded.size > 0) {
                // Need Rationale
                var message = "App need access to " + permissionsNeeded[0]
                for (i in 1 until permissionsNeeded.size) {
                    message = message + ", " + permissionsNeeded[i]
                }

                showMessageOKCancel(message) { dialog, which ->
                    requestPermissions( permissionsList.toTypedArray(),
                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS )
                }
            } else {
                requestPermissions( permissionsList.toTypedArray(),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)
            }

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
            if (!shouldShowRequestPermissionRationale(permission))
                return false
        }
        return true
    }

    private fun checkAllNecessaryPermissions(): List<String> {
        var unauthorizedPermissions: ArrayList<String> = ArrayList()

        for (permission in BTConstants.scanPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                unauthorizedPermissions.add(permission)
            }
        }

        return unauthorizedPermissions
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

            adapter.deviceInfoListener?.invoke(device)

            adapter.notifyDataSetChanged()
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

