package no.nordicsemi.android.ble.ble_gatt_client

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nedprotocol.NedPacket
import nedprotocol.PacketFactory
import no.nordicsemi.android.ble.ble_gatt_client.databinding.ActivityMain2Binding
import no.nordicsemi.android.ble.observer.ConnectionObserver

class MainActivity2: AppCompatActivity()  {

    private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: Int = 1001

    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var binding: ActivityMain2Binding

    private var device: BluetoothDevice? = null

    private val statusChangedChannel = Channel<String>()
    private val responseChannel = Channel<ByteArray>()

    private var gattServiceConn: MainActivity2.GattServiceConn? = null

    private var gattServiceData: GattService.DataPlane? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            stuffMarshMallow();
        }

        makeMainPageVisible(View.INVISIBLE)

        configCommandListener()

        binding.status.text = "设备已启动..."
        mainScope.launch {
            for (newValue in statusChangedChannel) {
                mainHandler.run {
                    binding.status.text = newValue

                    if (newValue == "设备已准备好") {
                        makeMainPageVisible(View.VISIBLE)
                    }
                }
            }
        }

        mainScope.launch {
            for (value in responseChannel) {
                mainHandler.run {
                    updateResponse(value)
                }
            }
        }

        // Startup our Bluetooth GATT service explicitly so it continues to run even if
        // this activity is not in focus
        startService(Intent(this, GattService::class.java))
    }

    private fun updateResponse(value: ByteArray) {
        val packet = NedPacket.parsePacket(value)

        when(packet?.commandCode) {
            NedPacket.NED_RESP_GET_DEVICE_INFO -> binding.respData.text = value.contentToString()
            NedPacket.NED_RESP_UPGRADE -> binding.respDataUpgradeInfo.text = value.contentToString()
            NedPacket.NED_RESP_GET_PLAIN_DATA -> binding.respDataPlainData.text = value.contentToString()
            NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE -> binding.sendUpgradePackage.text = value.contentToString()
            else -> binding.status.text = "响应数据错误~~~"
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

    @SuppressLint("MissingPermission")
    private fun updateDeviceInfo() {
        if (device != null) {
            binding.deviceName.text = device?.name
            binding.macAddress.text = device?.address
            binding.connectStatus.text = binding.status.text
        }
    }

    @SuppressLint("MissingPermission")
    fun makeMainPageVisible(visibility:Int) {
        binding.tagBasicInfo.visibility= visibility
        binding.tagDeviceInfo.visibility= visibility
        binding.tagUpgradeInfo.visibility= visibility
        binding.tagUpgradePackage.visibility= visibility
        binding.tagPlainData.visibility= visibility
    }

    private fun configCommandListener() {
        binding.sendDeviceInfo.setOnClickListener {
            if (device != null) {
                gattServiceData?.write(device!!, PacketFactory.packetForDeviceInfo())
            }
        }

        binding.sendPlainData.setOnClickListener {
            if (device != null) {
                gattServiceData?.write(device!!, PacketFactory.packetForPlainData())
            }
        }

        val packetForUpgradeInfo =
            PacketFactory.packetForUpgradeInfo(1001024, 0xFF00124, "asdlkjfaklsjdfa".toByteArray())
        binding.sendUpgradeInfo.setOnClickListener {
            if (device != null) {
                gattServiceData?.write(device!!, packetForUpgradeInfo)
            }
        }

        binding.reviewDataDeviceInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("获取设备信息")
                .setMessage(PacketFactory.packetForDeviceInfo().contentToString())
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

        binding.reviewDataPlainData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("获取常规数据")
                .setMessage(PacketFactory.packetForPlainData().contentToString())
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

        binding.reviewDataUpgradeInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("发送升级信息")
                .setMessage(packetForUpgradeInfo.contentToString())
                .setPositiveButton("OK", null)
                .create()
                .show()
        }

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

                gattServiceData?.setChannel(statusChangedChannel, responseChannel)

                gattServiceData?.setOnDevicesChangeListener {
                    if (it != null) {
                        gattServiceData?.connectDevice(it!!, DeviceConnectionCallback())
                    }

                }
            }

            gattServiceData?.enableServices()
        }
    }



    inner class DeviceConnectionCallback: ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            binding.status.text = "正在连接设备..."
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            binding.status.text = "设备已连接"
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            binding.status.text = "无法连接设备 - $reason"
            this@MainActivity2.device = null
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            binding.status.text = "设备已准备好"

            this@MainActivity2.device = device
            makeMainPageVisible(View.VISIBLE)
            updateDeviceInfo()
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            binding.status.text = "连接正在断开..."

            this@MainActivity2.device = null
            makeMainPageVisible(View.INVISIBLE)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            binding.status.text = "连接已断开"

            this@MainActivity2.device = null
            makeMainPageVisible(View.INVISIBLE)
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

                    // Permission Denied
                    Toast.makeText(
                        this,
                        "All Permission GRANTED !! Thank You :)",
                        Toast.LENGTH_SHORT
                    )
                        .show()
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
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) permissionsNeeded.add("Show Location")
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
}