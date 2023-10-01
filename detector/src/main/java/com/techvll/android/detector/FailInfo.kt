package com.techvll.android.detector

enum class FailInfo(val code:Int, val message:String, var extra:String?=null) {

    FailWriteCharacteristics(-1, "写特征值错误"),
    FailCommandCodeNotMatch(-2, "响应命令字不匹配"),
    FailRespPayloadNull(-3, "响应数据payload为Null"),
    FailArgumentsVerificationError(-4, "入参校验错误"),
    FailPackageSendError(-5, "升级包发送失败"),
    FailRespParseError(-6, "响应包解析错误"),
    FailDeviceBusy(-7, "设备忙，有进行中的请求"),
    FailDeviceDisconnected(-8, "设备未连接"),
    FailTimeout(-9, "消息发送或接收超时")
}