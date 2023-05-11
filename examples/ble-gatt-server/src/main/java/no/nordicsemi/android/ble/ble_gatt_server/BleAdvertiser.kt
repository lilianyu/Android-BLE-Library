package no.nordicsemi.android.ble.ble_gatt_server

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log
import spec.NedServiceProfile
import spec.NedServiceProfile.manufacturerData
import java.nio.ByteBuffer

object BleAdvertiser {
	private const val TAG = "ble-advertiser"

	class Callback : AdvertiseCallback() {
		override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
			Log.i(TAG, "LE Advertise Started.")
		}

		override fun onStartFailure(errorCode: Int) {
			Log.w(TAG, "LE Advertise Failed: $errorCode")
		}
	}

	fun settings(): AdvertiseSettings {
		return AdvertiseSettings.Builder()
				.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
				.setConnectable(true)
				.setTimeout(0)
				.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
				.build()
	}

	fun advertiseData(): AdvertiseData {

		return AdvertiseData.Builder()
				.setIncludeDeviceName(false) // Including it will blow the length
				.setIncludeTxPowerLevel(false)
				.addManufacturerData(0x5352, manufacturerData)
				.addServiceUuid(ParcelUuid(NedServiceProfile.NED_EVENT_SERVICE_UUID))
				.addServiceUuid(ParcelUuid(NedServiceProfile.NED_DATA_SERVICE_UUID))
				.build()
	}
}