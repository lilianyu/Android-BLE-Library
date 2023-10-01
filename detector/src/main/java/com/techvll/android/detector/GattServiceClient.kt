package com.techvll.android.detector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.elvishew.xlog.XLog


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
            ), this, Context.BIND_AUTO_CREATE)
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