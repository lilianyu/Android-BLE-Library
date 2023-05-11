package no.nordicsemi.android.ble.ble_gatt_client.entity

data class NewVersionCheckResult(val newVersionAvailable: Boolean,
                                 val newVersion: Long,
                                 val url: String,
                                 val md5: String,
                                 val desc: String)
