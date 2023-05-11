package utils

import java.math.BigInteger
import java.security.MessageDigest

object MD5 {
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun md5(input: ByteArray): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input)
        return printHexBinary(bytes)
    }

    fun printHexBinary(data: ByteArray): String {
        val r = StringBuilder(data.size * 2)
        data.forEach { b ->
            val i = b.toInt()
            r.append(HEX_CHARS[i shr 4 and 0xF])
            r.append(HEX_CHARS[i and 0xF])
        }
        return r.toString().substring(8,24)
    }
}

fun main() {
    val bytesString = "123456".toByteArray()

    val md5 = MD5.md5(bytesString)

    println("原始byte array: ${bytesString.contentToString()}")

    println("MD5摘要：${md5.substring(8, 24)}")
}