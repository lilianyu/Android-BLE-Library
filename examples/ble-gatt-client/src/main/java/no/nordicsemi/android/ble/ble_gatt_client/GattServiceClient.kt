package no.nordicsemi.android.ble.ble_gatt_client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.elvishew.xlog.XLog
import no.nordicsemi.android.ble.ble_gatt_client.BuildConfig


object GattServiceClient: ServiceConnection {

    var gattServiceProxy: GattService.DataPlane? = null

    fun isConnected(): Boolean {
        return gattServiceProxy != null
    }

    fun bindService(ctx: Context) {
        val bindService = ctx.bindService(
            Intent(
                GattService.DATA_PLANE_ACTION,
                null,
                ctx,
                GattService::class.java
            ), this, 0
        )
    }

    fun unbindService(ctx: Context) {
        ctx.unbindService(this)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (BuildConfig.DEBUG && GattService::class.java.name != name?.className)
            error("Connected to unknown service")
        else {
            gattServiceProxy = service as GattService.DataPlane
            XLog.i("onServiceConnected, gattServiceData = $gattServiceProxy")
        }

        gattServiceProxy?.enableServices()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        if (BuildConfig.DEBUG && GattService::class.java.name != name?.className) {
            error("Disconnected from unknown service")
        } else {
            gattServiceProxy = null
        }
    }


}