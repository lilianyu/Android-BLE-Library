package com.techvll.android.detector

import androidx.annotation.IntRange
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import nedprotocol.NedPacket

class NedRequest private constructor(val type: RequestType) {
    enum class RequestType {
        GET_DEVICE_INFO,
        GET_PLAIN_DATA,
        UPGRADE
    }

    companion object {
        fun nedUpgradeRequest(pkg: ByteArray, newVersion: Int): NedRequest {
            return NedRequest(RequestType.UPGRADE).apply {
                pkgBytes = pkg
                this.newVersion = newVersion
            }
        }

        fun nedDeviceInfoRequest(): NedRequest {
            return NedRequest(RequestType.GET_DEVICE_INFO)
        }

        fun nedPlainDataRequest(): NedRequest {
            return NedRequest(RequestType.GET_PLAIN_DATA)
        }
    }

    private var listeners = mutableListOf<NedRequestListener>()
    private var successCallbacks = mutableListOf<((packet: NedPacket?) -> Unit)>()
    private var failCallbacks = mutableListOf<((failInfo: FailInfo, packet: NedPacket?) -> Unit)>()
    private var progressCallbacks = mutableListOf<((packet:ByteArray, soFar:Int, totalSize:Int) -> Unit)>()
    private var timeoutCallbacks = mutableListOf<(() -> Unit)>()
    private lateinit var bleDevice: BleDevice
    private val logger: Logger = XLog.tag("NedRequest").build()

    //upgrade package info
    var pkgBytes: ByteArray? = null
    var newVersion: Int? = null

    protected var timeout: Long = 0

    @IntRange(from = 0)
    private var attempt = 0
    @IntRange(from = 0)
    private var retries = 0

    @IntRange(from = 0)
    var retryDelay:Long = 0

    var enqueued = false
    var started = false
    var finished = false

    fun setBleDevice(device: BleDevice): NedRequest {
        this.bleDevice = device
        return this
    }


    fun addListeners(listener: NedRequestListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NedRequestListener) {
        listeners.remove(listener)
    }

    fun done(callback: (packet: NedPacket?) -> Unit): NedRequest {
        this.successCallbacks.add(callback)
        return this
    }

    fun fail(callback: (failInfo: FailInfo, packet: NedPacket?) -> Unit): NedRequest {
        this.failCallbacks.add(callback)
        return this
    }

    fun progress(callback: (packet:ByteArray, soFar: Int, totalSize: Int) -> Unit): NedRequest {
        this.progressCallbacks.add(callback)
        return this
    }

    fun timeout(@IntRange(from = 0) timeout: Long): NedRequest {
        check(timeoutCallbacks.isEmpty()) { "Request already started" }
        this.timeout = timeout
        return this
    }

    fun retry(@IntRange(from = 0) count: Int): NedRequest {
        this.retries = count
        this.retryDelay = 0
        return this
    }

    fun retry(
        @IntRange(from = 0) count: Int,
        @IntRange(from = 0) delay: Long
    ): NedRequest {
        this.retries = count
        this.retryDelay = delay
        return this
    }

    fun canRetry(): Boolean {
        if (retries > 0) {
            retries -= 1
            return true
        }
        return false
    }

    fun isFirstAttempt(): Boolean {
        return attempt++ == 0
    }

    fun enqueue() {
        bleDevice?.processNedRequest(this)
    }

    fun notifyStarted() {
        started = true

        logger.i("request started~")

        listeners.forEach {
            it.onStart(bleDevice.device)
        }
    }

    fun notifyDone(packet: NedPacket?) {
        logger.i("request finished~")

        bleDevice?.isRequestOngoing = false
        finished = true

        listeners.forEach {
            it.onCompleted(bleDevice.device, packet)
        }

        successCallbacks.forEach { successCallback ->
            successCallback.invoke(packet)
        }
    }

    fun notifyFail(failInfo: FailInfo, packet: NedPacket?) {
        logger.i("request failed with $failInfo, ${packet?.packet}")

        bleDevice?.isRequestOngoing = false
        finished = true
        failCallbacks.forEach { failCallback ->
            failCallback.invoke(failInfo, packet)
        }

        listeners.forEach {
            it.onFail(bleDevice.device, failInfo, packet)
        }
    }

    fun notifyProgress(packet:ByteArray, soFar:Int, totalSize:Int) {
        progressCallbacks.forEach { progressCallback ->
            progressCallback.invoke(packet, soFar, totalSize)
        }

        listeners.forEach {
            it.onProgress(bleDevice.device, packet, soFar, totalSize)
        }
    }

    fun checkResponse(packet:NedPacket): Boolean {
        when (type) {
            RequestType.GET_DEVICE_INFO -> {
                return if (packet.commandCode == NedPacket.NED_RESP_GET_DEVICE_INFO) {
                    true
                } else {
                    notifyFail(FailInfo.FailCommandCodeNotMatch.apply {
                        extra = "expected ${NedPacket.NED_RESP_GET_DEVICE_INFO}, actually get ${packet.commandCode}"
                    }, packet)
                    false
                }
            }
            RequestType.GET_PLAIN_DATA -> {
                return if (packet.commandCode == NedPacket.NED_RESP_GET_PLAIN_DATA) {
                    true
                } else {
                    notifyFail(FailInfo.FailCommandCodeNotMatch.apply {
                        extra = "expected ${NedPacket.NED_RESP_GET_PLAIN_DATA}, actually get ${packet.commandCode}"
                    }, packet)
                    false
                }
            }
            RequestType.UPGRADE -> {
                return if (packet.commandCode == NedPacket.NED_RESP_UPGRADE
                    || packet.commandCode == NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE) {
                    true
                } else {
                    notifyFail(FailInfo.FailCommandCodeNotMatch.apply {
                        extra = "expected ${NedPacket.NED_RESP_UPGRADE} or ${NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE}, actually get ${packet.commandCode}"
                    }, packet)
                    false
                }
            }
        }
    }
}