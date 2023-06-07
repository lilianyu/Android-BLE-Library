package no.nordicsemi.android.ble.ble_gatt_client

import android.os.Build

object BTConstants {

    val permissionsRationals = mapOf (Pair(android.Manifest.permission.ACCESS_FINE_LOCATION, "申请位置权限，用于泰科检测仪的发现和扫描"),
        Pair(android.Manifest.permission.BLUETOOTH_CONNECT, "申请蓝牙连接权限，用于连接周边的泰科检测仪设备"),
        Pair(android.Manifest.permission.BLUETOOTH_SCAN, "申请蓝牙扫描权限，用于扫描周边的泰科检测仪设备"),
        Pair(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, "申请设备读写权限，用于缓存必要的数据")
    )

    val scanPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        listOf (
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN)
    else listOf (android.Manifest.permission.ACCESS_FINE_LOCATION)

}