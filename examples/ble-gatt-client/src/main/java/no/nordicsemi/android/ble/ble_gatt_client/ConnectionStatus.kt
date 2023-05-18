package no.nordicsemi.android.ble.ble_gatt_client

enum class ConnectionStatus(val status: Int, var imageResource: Int) {
    NotStarted(-1, R.drawable.cs_notstarted),
    Connecting(0, R.drawable.cs_connecting),
    Connected(1, R.drawable.cs_connected),
    FailedToConnect(2, R.drawable.cs_failed),
    Ready(3, R.drawable.cs_ready),
    Disconnecting(4, R.drawable.cs_disconnecting),
    Disconnected(5, R.drawable.cs_disconnected)
}