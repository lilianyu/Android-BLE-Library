package com.techvll.android.detector

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.techvll.android.detector.databinding.ActivityDeviceManagerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nedprotocol.NedPacket
import nedprotocol.PacketFactory
import java.util.*

class DeviceManagerActivity: AppCompatActivity()  {

    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var binding: ActivityDeviceManagerBinding

    private var device: BluetoothDevice? = null

    private val statusChangedChannel = Channel<String>()

    private var gattServiceConn: GattServiceConn? = null

    private var gattServiceData: GattService.DataPlane? = null


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDeviceManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        device = intent.getParcelableExtra("device")

        updateDeviceInfo()
        makeMainPageVisible(View.VISIBLE)

        configCommandListener()

        title = if (device?.name.isNullOrEmpty()) "未命名设备" else device?.name

        // Startup our Bluetooth GATT service explicitly so it continues to run even if
        // this activity is not in focus
//        startService(Intent(this, GattService::class.java))
    }

    private fun updateResponse(nedPacket: NedPacket) {

        mainScope.launch {
            when(nedPacket?.commandCode) {
                NedPacket.NED_RESP_GET_DEVICE_INFO -> binding.respData.text = nedPacket.packet?.map { "%02X".format(it) }.toString()
                NedPacket.NED_RESP_GET_PLAIN_DATA -> binding.respDataPlainData.text = nedPacket.packet?.map { "%02X".format(it) }.toString()
                NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE -> binding.respUpgradePackage.text = nedPacket.packet?.map { "%02X".format(it) }.toString()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        return super.onCreateOptionsMenu(menu)
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

    @SuppressLint("MissingPermission")
    private fun updateDeviceInfo() {
        if (device != null) {
            binding.deviceName.text = device?.name
            binding.macAddress.text = device?.address
            binding.connectStatus.text = "准备好"
        }
    }

    @SuppressLint("MissingPermission")
    fun makeMainPageVisible(visibility:Int) {
        binding.tagBasicInfo.visibility= visibility
        binding.tagDeviceInfo.visibility= visibility
        binding.tagUpgradePackage.visibility= visibility
        binding.tagPlainData.visibility= visibility
    }

    private fun configCommandListener() {
//        binding.sendDeviceInfo.setOnClickListener {
//            device?.let{
//                gattServiceData?.getDeviceInfo(it)
//                    ?.apply {
//                        fail { failInfo: FailInfo, nedPacket: NedPacket? ->
//                            mainScope.launch {
//                                var errorMessage = "${failInfo.message}"
//                                failInfo.extra?.let {extra ->
//                                    errorMessage = "${errorMessage}, $extra"
//                                }
//                                nedPacket?.let { nedPacket ->
//                                    errorMessage = "${errorMessage} - ${nedPacket.packet?.map {byte ->  "%02X".format(byte) }.toString()}"
//                                }
//
//                                binding.respData.text = errorMessage
//                            }
//                        }
//                        done {
//                            mainScope.launch {
//                                val hexBytes = it?.packet?.map { byte ->
//                                    "%02X".format(byte)
//                                }
//
//                                binding.respData.text = hexBytes.toString()
//                            }
//                        }
//                    }?.enqueue()
//            }
//        }
//
//        binding.sendPlainData.setOnClickListener {
//            device?.let{
//                gattServiceData?.getPlainData(it)?.apply {
//                    fail { failInfo: FailInfo, nedPacket: NedPacket? ->
//                        mainScope.launch {
//
//                            var errorMessage = "${failInfo.message}"
//                            failInfo.extra?.let {extra ->
//                                errorMessage = "${errorMessage}, $extra"
//                            }
//                            nedPacket?.let { nedPacket ->
//                                errorMessage = "${errorMessage} - ${nedPacket.packet?.map {byte ->  "%02X".format(byte) }.toString()}"
//                            }
//
//                            binding.respDataPlainData.text = errorMessage
//                        }
//                    }
//                    done {
//                        mainScope.launch {
//                            val hexBytes = it?.packet?.map { byte ->
//                                "%02X".format(byte)
//                            }
//                            binding.respDataPlainData.text = hexBytes.toString()
//                        }
//                    }
//                }?.enqueue()
//            }
//        }
//
//        val bytes = assets.open("ChargingPointMaster.bin")
////        val inputStream = assets.open("registration.pdf")
////        val inputStream = assets.open("edit.svg")
//         .use {
//            val fileBytes = ByteArray(it.available())
//            it.read(fileBytes)
//            fileBytes
//        }
//
//        val packetForUpgradeInfo =
//            PacketFactory.packetForUpgradeInfo(bytes.size, 0xFF001243.toInt(), "asdlkjfaklsjdfa".toByteArray())
//
//        binding.sendUpgradePackage.setOnClickListener {
//            device?.let{
//                binding.sendUpgradePackage.isEnabled = false
//                gattServiceData?.upgradePackage(it, bytes, 0xFF001243.toInt())?.apply {
//                    progress { packet: ByteArray, soFar:Int, totalSize:Int ->
//                        mainScope.launch {
////                            val packetString = packet?.map { "%02X".format(it) }.toString()
//                            binding.respUpgradePackage.text = "已发送${soFar}包数据"
//                        }
//                    }
//
//                    done {
//                        mainScope.launch {
//                            binding.sendUpgradePackage.isEnabled = true
//                            binding.respUpgradePackage.text = "发送完成"
//                        }
//                    }
//
//                    fail { failInfo: FailInfo, nedPacket: NedPacket? ->
//                        mainScope.launch {
//                            binding.sendUpgradePackage.isEnabled = true
//
//                            var errorMessage = "${failInfo.message}"
//                            failInfo.extra?.let {extra ->
//                                errorMessage = "${errorMessage}, $extra"
//                            }
//                            nedPacket?.let { nedPacket ->
//                                errorMessage = "$errorMessage - ${nedPacket.packet?.map { byte ->  "%02X".format(byte) }.toString()}"
//                            }
//
//                            binding.respUpgradePackage.text = errorMessage
//                        }
//                    }
//                }?.enqueue()
//            }
//        }

        binding.reviewDataDeviceInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("获取设备信息")
                .setMessage(PacketFactory.packetForDeviceInfo().packet?.map { "%02X".format(it) }.toString())
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

        binding.reviewDataPlainData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("获取常规数据")
                .setMessage(PacketFactory.packetForPlainData().packet?.map { "%02X".format(it) }.toString())
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

//        binding.reviewDataUpgradePackage.setOnClickListener {
//            AlertDialog.Builder(this)
//                .setTitle("发送升级包")
//                .setMessage(packetForUpgradeInfo.packet?.map { "%02X".format(it) }.toString())
//                .setPositiveButton("OK", null)
//                .create()
//                .show()
//        }

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

                gattServiceData?.setChannel(statusChangedChannel)
            }

            gattServiceData?.enableServices()
        }
    }
}