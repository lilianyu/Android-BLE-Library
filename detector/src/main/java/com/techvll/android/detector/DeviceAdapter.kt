package com.techvll.android.detector

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.techvll.android.detector.databinding.DeviceItemNewBinding

class DeviceAdapter (
): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> () {

    companion object {
        val CMD_CONNECT = 0
        val CMD_DISCONNECT = 1
    }

    var connectListener:((DeviceAdapterItem, Int) -> Unit)? = null
    var deviceInfoListener:((BluetoothDevice) -> Unit)? = null
    var upgradeListener:((DeviceAdapterItem) -> Unit)? = null
    var checkNewVersion:((DeviceAdapterItem) -> Unit)? = null

    var deviceList = mutableListOf<DeviceAdapterItem>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        var binding: DeviceItemNewBinding = DeviceItemNewBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return DeviceViewHolder(binding)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.binding.deviceName.text = if (deviceList[position].device.name.isNullOrEmpty()) "未命名设备" else deviceList[position].device.name
        holder.binding.macAddressValue.text = deviceList[position].addressReadable
        holder.binding.deviceStatus.setImageResource(deviceList[position].connectStatus.imageResource)

        holder.binding.hardwareVersionValue.text = deviceList[position].hardwareVersionReadable
        holder.binding.softwareVersionValue.text = deviceList[position].softwareVersionReadable

        if (deviceList[position].stUpgrade == 0) {
            holder.binding.tvUpgrading.visibility = View.GONE
            holder.binding.tvProgress.visibility = View.GONE
        } else if (deviceList[position].stUpgrade == 1) {
            holder.binding.tvUpgrading.visibility = View.VISIBLE
            holder.binding.tvProgress.visibility = View.VISIBLE
            holder.binding.tvUpgrading.text = "升级中"
            holder.binding.tvProgress.text = "${deviceList[position].upgradeProgress}%"
        } else if (deviceList[position].stUpgrade == 2) {
            holder.binding.tvUpgrading.visibility = View.VISIBLE
            holder.binding.tvProgress.visibility = View.VISIBLE
            holder.binding.tvProgress.text = "${deviceList[position].upgradeProgress}%"

            holder.binding.tvUpgrading.text = "已升级"
        }

        if (deviceList[position].connectStatus == ConnectionStatus.NotStarted ||
            deviceList[position].connectStatus == ConnectionStatus.FailedToConnect ||
            deviceList[position].connectStatus == ConnectionStatus.Disconnected
        ) {

            if (holder.binding.btnConnect.isEnabled) {
                holder.binding.btnConnect.text = "建立连接"
            }
        } else {
            holder.binding.btnConnect.text = "断开连接"
            holder.binding.btnConnect.isEnabled = true
        }

        holder.binding.btnConnect.setOnClickListener {
            if (deviceList[position].connectStatus == ConnectionStatus.NotStarted ||
                deviceList[position].connectStatus == ConnectionStatus.FailedToConnect ||
                deviceList[position].connectStatus == ConnectionStatus.Disconnected
            ) {

                holder.binding.btnConnect.isEnabled = false
                holder.binding.btnConnect.text = "连接中..."
                connectListener?.invoke(deviceList[position], CMD_CONNECT)
            } else {
                connectListener?.invoke(deviceList[position], CMD_DISCONNECT)
            }
        }

        holder.binding.checkToUpgrade.setOnClickListener {
            checkNewVersion?.invoke(deviceList[position])
        }

        holder.binding.btnUpgrade.visibility = View.GONE
        holder.binding.btnUpgrade.setOnClickListener {
            upgradeListener?.invoke(deviceList[position])
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    class DeviceViewHolder(val binding: DeviceItemNewBinding) : RecyclerView.ViewHolder(binding.root)

    data class DeviceAdapterItem(val device: BluetoothDevice) {
        var connectStatus: ConnectionStatus = ConnectionStatus.NotStarted
        var versionToUpgrade: UInt? = null
        var hardwareVersion: UInt? = null
        var softwareVersion: UInt? = null
        var address: ByteArray? = null
        var stUpgrade: Int = 0 //0:未开始 1:升级中 2:已完成
        var upgradeProgress: UInt = 0u

        val hardwareVersionReadable: String
            get() = when (hardwareVersion) {
                null -> "未连接"
                else -> {
                    "${hardwareVersion?.shr(24)?.toUByte()}.${hardwareVersion?.shr(16)?.toUByte()}.${hardwareVersion?.shr(8)?.toUByte()}.${hardwareVersion?.toUByte()}"
                }
            }

        val softwareVersionReadable: String
            get() = when (softwareVersion) {
                null -> "未连接"
                else -> {
                    "${softwareVersion?.shr(24)?.toUByte()}.${softwareVersion?.shr(16)?.toUByte()}.${softwareVersion?.shr(8)?.toUByte()}.${softwareVersion?.toUByte()}"
                }
            }

        val addressReadable: String
            get() = when (address) {
                null -> "未连接"
                else -> address!!?.map {byte ->  "%02X".format(byte) }!!.joinToString(".")
            }

    }
}