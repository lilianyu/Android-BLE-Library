package no.nordicsemi.android.ble.ble_gatt_client

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ble_gatt_client.entity.PackageInfo
import no.nordicsemi.android.ble.ble_gatt_client.entity.User
import no.nordicsemi.android.ble.ble_gatt_client.net.RetrofitClient

class UserViewModel : ViewModel() {

    val user by lazy {
        MutableLiveData<User>()
    }

    val packageInfo by lazy {
        MutableLiveData<PackageInfo>()
    }

    fun getUser(id: Long) {
        viewModelScope.launch {
            val result = RetrofitClient.apiService.getUserById(id)
            user.value = result.data
//            contentList.value = articleList.data
            Log.d("ViewPagerViewModel", "getUser: $user")
        }
    }

    fun checkNewVersion(macAddress:String, hwVersion:Long, swVersion:Long) {

        viewModelScope.launch {
            val result = RetrofitClient.apiService.checkNewVersion(null, macAddress,
                hwVersion, swVersion)

            packageInfo.value = result.data

            Log.d("ViewPagerViewModel", "checkNewVersion: ${packageInfo.value}")
        }

    }

//    fun checkNewVersion()
}