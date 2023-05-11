package no.nordicsemi.android.ble.ble_gatt_client

enum class ConnectionStatus(val status: Int, var message: String) {
    NotStarted(-1, "未开始"),
    Connecting(0, "连接中..."),
    Connected(1, "已连接"),
    FailedToConnect(2, "连接失败"),
    Ready(3, "准备好"),
    Disconnecting(4, "断开中..."),
    Disconnected(5, "已断开")
}