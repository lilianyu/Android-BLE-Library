package com.techvll.android.detector

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.techvll.android.detector.entity.PackageInfo
import com.techvll.android.detector.entity.User
import com.techvll.android.detector.net.RetrofitClient

class UserViewModel : ViewModel() {

    val user by lazy {
        MutableLiveData<User>()
    }

    val packageInfo by lazy {
        MutableLiveData<PackageInfo>()
    }

    var newVersionError:String? = null

    var currentDeviceToUpgrade: BluetoothDevice? = null

    fun getUser(id: Long) {
        viewModelScope.launch {
            val result = RetrofitClient.apiService.getUserById(id)
            user.value = result.data
//            contentList.value = articleList.data
            Log.d("ViewPagerViewModel", "getUser: $user")
        }
    }

    fun checkNewVersion(item: DeviceAdapter.DeviceAdapterItem, hwVersion:Long, swVersion:Long) {

        viewModelScope.launch {
            newVersionError = null

            if (currentDeviceToUpgrade != null) {
                newVersionError = "当前有设备正在升级中，请稍后再试"
            }

            try {
                val result = RetrofitClient.apiService.checkNewVersion(null, item.addressReadable,
                    hwVersion, swVersion)

                if (result.code != 0) {
                    newVersionError = "${result.message}(${result.code})"
                }

                currentDeviceToUpgrade = item.device
                packageInfo.value = result.data
            } catch (e: Exception) {
                newVersionError = "连接出错：${e.message}，请检查网络后重试"
                packageInfo.value = null
            }

            Log.d("ViewPagerViewModel", "checkNewVersion: ${packageInfo.value}")
        }
    }

//    fun checkNewVersion()
}