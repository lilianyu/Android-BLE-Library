package no.nordicsemi.android.ble.ble_gatt_client.net

import no.nordicsemi.android.ble.ble_gatt_client.entity.NewVersionCheckResult
import no.nordicsemi.android.ble.ble_gatt_client.entity.User
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("evd/user/{id}")
    suspend fun getUserById(@Path("id") id: Long): ResultData<User>

    @GET("evd/checkNewVersion")
    suspend fun checkNewVersion(@Query("userId") userId: Long,
                                @Query("productId") productId: Long,
                                @Query("macAddress") macAddress: String,
                                @Query("versionCode") versionCode: Long): ResultData<NewVersionCheckResult>
}