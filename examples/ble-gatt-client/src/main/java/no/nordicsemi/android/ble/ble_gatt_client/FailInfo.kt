package no.nordicsemi.android.ble.ble_gatt_client

enum class FailInfo(val code:Int, val message:String, var extra:String?=null) {

    Fail_WriteCharacterisc(-1, "写特征值错误"),
    Fail_CommandCodeNotMatch(-2, "响应命令字不匹配"),
    Fail_RespPayloadNull(-3, "响应数据payload为Null"),
    Fail_ArgumentsVerificationError(-4, "入参校验错误"),
    Fail_PackageSendError(-5, "升级包发送失败"),
    Fail_RespParseError(-6, "响应包解析错误")

}