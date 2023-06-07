package no.nordicsemi.android.ble.ble_gatt_client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import no.nordicsemi.android.ble.ble_gatt_client.databinding.DeviceItemNewBinding

class DeviceAdapter (
): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> () {

    companion object {
        val CMD_CONNECT = 0
        val CMD_DISCONNECT = 1
    }

    var connectListener:((DeviceAdapterItem, Int) -> Unit)? = null
    var deviceInfoListener:((DeviceAdapterItem) -> Unit)? = null
    var upgradeListener:((DeviceAdapterItem) -> Unit)? = null
    var deviceList = mutableListOf<DeviceAdapterItem>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        var binding:DeviceItemNewBinding = DeviceItemNewBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return DeviceViewHolder(binding)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.binding.deviceName.text = if (deviceList[position].device.name.isNullOrEmpty()) "未命名设备" else deviceList[position].device.name
        holder.binding.macAddressValue.text = deviceList[position].device.address
        holder.binding.deviceStatus.setImageResource(deviceList[position].connectStatus.imageResource)

        holder.binding.hardwareVersionValue.text = deviceList[position].hardwareVersionReadable
        holder.binding.softwareVersionValue.text = deviceList[position].softwareVersionReadable

        if (deviceList[position].connectStatus == ConnectionStatus.NotStarted ||
            deviceList[position].connectStatus == ConnectionStatus.FailedToConnect ||
            deviceList[position].connectStatus == ConnectionStatus.Disconnected ) {

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
                deviceList[position].connectStatus == ConnectionStatus.Disconnected ) {

                holder.binding.btnConnect.isEnabled = false
                holder.binding.btnConnect.text = "连接中..."
                connectListener?.invoke(deviceList[position], CMD_CONNECT)
            } else {
                connectListener?.invoke(deviceList[position], CMD_DISCONNECT)
            }
        }

        holder.binding.checkToUpgrade.setOnClickListener {
            deviceInfoListener?.invoke(deviceList[position])
        }

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

    }
}