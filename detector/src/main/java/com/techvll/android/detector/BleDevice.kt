package com.techvll.android.detector

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import nedprotocol.NedPacket
import nedprotocol.NedParseException
import nedprotocol.PacketFactory
import no.nordicsemi.android.ble.BleManager
import spec.NedServiceProfile
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min


class BleDevice(context: Context, val device: BluetoothDevice) : BleManager(context) {
    companion object {
        private const val TAG = "BleDevice"
        const val MAX_PAYLOAD_SIZE = 512
        const val MAX_PACKET_SIZE = 512 + 9
    }

    var isRequestOngoing: Boolean = false

    var hardwareVersion: UInt? = null
    var softwareVersion: UInt? = null
    var macAddress: ByteArray? = null

    private val logger: Logger = XLog.tag(TAG).build()

    private var defaultScope = CoroutineScope(Dispatchers.IO)

    private var internalChannel = Channel<ByteArray>()

    private var eventCharacteristic: BluetoothGattCharacteristic? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback = GattCallback()

    private suspend fun getDeviceInfo(nedRequest: NedRequest): BleDevice {
        write(PacketFactory.packetForDeviceInfo(), nedRequest)?.let {
            if (nedRequest.checkResponse(it)) {
                if (it.payload == null) {
                    nedRequest.notifyFail(FailInfo.FailRespPayloadNull, it)
                } else {
                    it.payload?.let { payload ->
                        ByteBuffer.wrap(payload).apply {
                            hardwareVersion = getInt(0).toUInt()
                            softwareVersion = getInt(4).toUInt()
                            macAddress = ByteArray(16) { index ->
                                get(8 + index)
                            }
                        }

                        nedRequest.notifyDone(it)
                    }
                }
            }
        }

        return this
    }

    private suspend fun getPlainData(nedRequest: NedRequest): BleDevice {

        write(PacketFactory.packetForPlainData(), nedRequest)?.let {
            if (nedRequest.checkResponse(it)) {
                if (it.payload == null) {
                    nedRequest.notifyFail(FailInfo.FailRespPayloadNull, it)
                } else {
                    it.payload?.let { payload ->
                    }

                    nedRequest.notifyDone(it)
                }
            }
        }

        return this
    }

    private suspend fun upgradePackage(nedRequest: NedRequest): BleDevice {
        if (nedRequest.newVersion == null) {
            nedRequest.notifyFail(FailInfo.FailArgumentsVerificationError.apply {
                extra = "升级操作，新版本号不能为Null"
            }, null)
            return this
        }

        nedRequest.newVersion?.let { newVersion ->
            softwareVersion?.let { oldVersion ->
                if (newVersion.toUInt() <= oldVersion) {
                    nedRequest.notifyFail(FailInfo.FailArgumentsVerificationError.apply {
                        extra = "升级操作无法降级，新版本号：${newVersion}，旧版本号: ${oldVersion}"
                    }, null)
                    return this
                }
            }
        }

        nedRequest.pkgBytes?.let { pkg ->
            val md5 = MessageDigest.getInstance("MD5").digest(pkg)
            val packetForUpgradeInfo = PacketFactory.packetForUpgradeInfo(pkg.size, nedRequest.newVersion!!, md5)

            write(packetForUpgradeInfo, nedRequest)?.also {
                if (nedRequest.checkResponse(it)) {
                    if (it.payload == null) {
                        nedRequest.notifyFail(FailInfo.FailRespPayloadNull, it)
                    }
                }
            } ?: return this

            var index = 0
            val totalSize = pkg.size
            var continueLoop = true

            while (continueLoop) {
                val from = index * MAX_PAYLOAD_SIZE
                val to = min(from + MAX_PAYLOAD_SIZE, totalSize)

                if (from >= totalSize) {
                    nedRequest.notifyDone(null)
                    return this
                }

                val packet =
                    PacketFactory.packetForPackage(pkg.copyOfRange(from, to), index.toUShort())

                write(packet, nedRequest)?.also {
                    it.payload?.let { payload ->
                        ByteBuffer.wrap(payload).apply {
                            when (get(0).toInt()) {
                                0 -> { //本包数据成功
                                    ++index
                                    nedRequest.notifyProgress(it.packet!!, to, totalSize)

                                    Log.i(TAG, "packet No.${index} has been sent successfully!")
                                }
                                1 -> { //本包发送失败
                                    Log.w(TAG, "packet No.${index} failed to send, retry!")

                                    if (nedRequest.canRetry()) {
                                        delay(nedRequest.retryDelay)
                                    } else {
                                        continueLoop = false
                                        nedRequest.notifyFail(FailInfo.FailPackageSendError.apply {
                                            extra = "升级包发送重试超限"
                                        }, it)
                                    }
                                }
                                2 -> { //全部接收成功
                                    continueLoop = false
                                    nedRequest.notifyDone(it)
                                }
                                3 -> { //接收完成，MD5校验错误
                                    continueLoop = false
                                    nedRequest.notifyFail(FailInfo.FailPackageSendError.apply {
                                        extra = "MD5校验不通过"
                                    }, it)
                                }
                            }
                        }
                    }
                } ?: return this
            }
        }

        return this
    }

    fun processNedRequest(nedRequest: NedRequest) {
        nedRequest.notifyStarted()

        if (isRequestOngoing) {
            nedRequest.notifyFail(FailInfo.FailDeviceBusy, null)
            return
        }

        isRequestOngoing = true
        when(nedRequest.type) {
            NedRequest.RequestType.GET_DEVICE_INFO -> {
                defaultScope.launch {
                    getDeviceInfo(nedRequest)
                }
            }
            NedRequest.RequestType.GET_PLAIN_DATA -> {
                defaultScope.launch {
                    getPlainData(nedRequest)
                }
            }
            NedRequest.RequestType.UPGRADE -> {
                defaultScope.launch {
                    upgradePackage(nedRequest)
                }
            }
        }
    }

    private suspend fun write(reqPacket: NedPacket, nedRequest: NedRequest): NedPacket? {

        var nedPacket:NedPacket? = null

        XLog.i("new packet starts: size = ${reqPacket.packet?.size}")
        val timeout = withTimeoutOrNull(3000) {
            writeCharacteristic(
                dataCharacteristic,
                reqPacket.packet,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .fail { _: BluetoothDevice, status: Int ->
                    nedRequest.notifyFail(FailInfo.FailWriteCharacteristics.apply {
                        extra = "writeCharacteristic fail status = $status"
                    }, null)
                }
                .split()
                .enqueue()

            val respByteArray = internalChannel.receive()

            try {
                XLog.i("收到数据, $respByteArray")

                nedPacket = NedPacket.parsePacket(respByteArray!!)

            } catch (e: NedParseException) {
                XLog.e("响应包解析错误, $e")

                nedRequest.notifyFail(FailInfo.FailRespParseError.apply {
                    extra = "$e"
                }, null)
            }
        }

        if (timeout == null) {
            nedRequest.notifyFail(FailInfo.FailTimeout, null)
        }

        return nedPacket
    }


    private inner class GattCallback: BleManagerGattCallback() {
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
            requestMtu(243)
                .enqueue()

            var numReceived = 0
            var totalLength = 0
            setNotificationCallback(eventCharacteristic)
                .merge {
                        output, lastPacket, index ->

                    lastPacket?.let {
                        output.write(lastPacket)

                        if (index == 0) {
                            totalLength = 0
                            numReceived = lastPacket.size
                        } else {
                            numReceived += lastPacket.size
                        }

                        //还未解析长度
                        if (numReceived>=4 && totalLength==0) {
                            totalLength = NedPacket.parseLength(output.toByteArray())
                        }
                    }

                    if (totalLength==0) false else numReceived >= totalLength

                }.with { _, data ->
                    data.value?.let {
                        defaultScope.launch {
                            internalChannel.send(it)
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


    override fun log(priority: Int, message: String) {
//        if (BuildConfig.DEBUG || priority == Log.ERROR) {
//            Log.println(priority, TAG, message)
//        }

        logger.log(priority, message)
    }

}