package no.nordicsemi.android.ble.ble_gatt_client

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ble_gatt_client.entity.User
import no.nordicsemi.android.ble.ble_gatt_client.net.RetrofitClient

class UserViewModel : ViewModel() {

    val user by lazy {
        MutableLiveData<User>()
    }

    fun getUser(id: Long) {
        viewModelScope.launch {
            val result = RetrofitClient.apiService.getUserById(id)
            user.value = result.data
//            contentList.value = articleList.data
            Log.d("ViewPagerViewModel", "getUser: $user")
        }
    }

//    fun checkNewVersion()
}