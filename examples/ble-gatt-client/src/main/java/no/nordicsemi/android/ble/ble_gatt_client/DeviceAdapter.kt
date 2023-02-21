package no.nordicsemi.android.ble.ble_gatt_client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import no.nordicsemi.android.ble.ble_gatt_client.databinding.DeviceItemBinding
import no.nordicsemi.android.ble.observer.ConnectionObserver

class DeviceAdapter (
): RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> () {

    var connectListener:((BluetoothDevice) -> Unit)? = null
    var writeListener:((BluetoothDevice) -> Unit)? = null

    var deviceList = mutableListOf<BluetoothDevice>()

    var connectionState = mutableMapOf<BluetoothDevice, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        var binding:DeviceItemBinding = DeviceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return DeviceViewHolder(binding)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.binding.deviceName.text = deviceList[position].name
        holder.binding.macAddress.text = deviceList[position].address
        holder.binding.state.text = when(deviceList[position].bondState) {
            10 -> "None"
            11 -> "Bonding"
            12 -> "Bonded"
            else -> "Unknown"
        }

        if (connectionState[deviceList[position]].isNullOrEmpty()) {
            holder.binding.connectState.text = "Waiting"
        } else {
            holder.binding.connectState.text = connectionState[deviceList[position]]
        }
        holder.binding.connect.setOnClickListener {
            connectListener?.invoke(deviceList[position])
        }

        holder.binding.write.setOnClickListener {
            writeListener?.invoke(deviceList[position])
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    class DeviceViewHolder(val binding: DeviceItemBinding) : RecyclerView.ViewHolder(binding.root)

    inner class DeviceConnectionCallback: ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            connectionState[device] = "Connecting"

            notifyDataSetChanged()
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            connectionState[device] = "Connected"

            notifyDataSetChanged()
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            connectionState[device] = "FailedToConnect - $reason"

            notifyDataSetChanged()
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            connectionState[device] = "Ready"

            notifyDataSetChanged()
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            connectionState[device] = "Disconnecting"

            notifyDataSetChanged()
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            connectionState[device] = "Disconnected"

            notifyDataSetChanged()
        }

    }

}