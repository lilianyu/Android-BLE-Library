package no.nordicsemi.android.ble.ble_gatt_client

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

    private var successCallback: ((packet: NedPacket?) -> Unit)? = null
    private var failCallback: ((failInfo: FailInfo, packet: NedPacket?) -> Unit)? = null
    private var progressCallback: ((packet:ByteArray, index:Int) -> Unit)? = null
    private var timeoutCallback: (() -> Unit)? = null
    private var requestHandler: BleDevice? = null
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

    fun setRequestHandler(requestHandler: BleDevice): NedRequest {
        this.requestHandler = requestHandler
        return this
    }

    fun done(callback: (packet: NedPacket?) -> Unit): NedRequest {
        this.successCallback = callback
        return this
    }

    fun fail(callback: (failInfo: FailInfo, packet: NedPacket?) -> Unit): NedRequest {
        this.failCallback = callback
        return this
    }

    fun progress(callback: (packet:ByteArray, index:Int) -> Unit): NedRequest {
        this.progressCallback = callback
        return this
    }

    fun timeout(@IntRange(from = 0) timeout: Long): NedRequest {
        check(timeoutCallback == null) { "Request already started" }
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

        requestHandler?.processNedRequest(this)
    }

    fun notifyStarted() {
        started = true

        logger.i("request started~")
    }

    fun notifyDone(packet: NedPacket?) {
        logger.i("request finished~")

        finished = true
        successCallback?.invoke(packet)
    }

    fun notifyFail(failInfo: FailInfo, packet: NedPacket?) {
        logger.i("request failed with $failInfo, ${packet?.packet}")

        finished = true
        failCallback?.invoke(failInfo, packet)
    }

    fun notifyProgress(packet:ByteArray, index:Int) {
        progressCallback?.invoke(packet, index)
    }

    fun checkResponse(packet:NedPacket): Boolean {
        when (type) {
            RequestType.GET_DEVICE_INFO -> {
                return if (packet.commandCode == NedPacket.NED_RESP_GET_DEVICE_INFO) {
                    true
                } else {
                    notifyFail(FailInfo.Fail_CommandCodeNotMatch.apply {
                        extra = "expected ${NedPacket.NED_RESP_GET_DEVICE_INFO}, actually get ${packet.commandCode}"
                    }, packet)
                    false
                }
            }
            RequestType.GET_PLAIN_DATA -> {
                return if (packet.commandCode == NedPacket.NED_RESP_GET_PLAIN_DATA) {
                    true
                } else {
                    notifyFail(FailInfo.Fail_CommandCodeNotMatch.apply {
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
                    notifyFail(FailInfo.Fail_CommandCodeNotMatch.apply {
                        extra = "expected ${NedPacket.NED_RESP_UPGRADE} or ${NedPacket.NED_RESP_SEND_UPGRADE_PACKAGE}, actually get ${packet.commandCode}"
                    }, packet)
                    false
                }
            }
        }
    }
}