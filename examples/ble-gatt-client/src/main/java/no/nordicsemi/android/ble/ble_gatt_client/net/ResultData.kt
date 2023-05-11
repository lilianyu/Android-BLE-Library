package no.nordicsemi.android.ble.ble_gatt_client.net

data class ResultData<T>(
    val code: Int,
    val message: String,
    val data: T
)