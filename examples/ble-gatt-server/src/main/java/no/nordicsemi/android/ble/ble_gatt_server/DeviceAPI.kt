package no.nordicsemi.android.ble.ble_gatt_server

import no.nordicsemi.android.ble.observer.ServerObserver


interface DeviceAPI {
	/**
	 * Change the value of the GATT characteristic that we're publishing
	 */
	fun setEventCharacteristicValue(value: String)

	fun setOuterServerObserver(observer: ServerObserver?)

}