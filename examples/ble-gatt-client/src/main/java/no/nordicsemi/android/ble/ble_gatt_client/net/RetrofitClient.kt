package no.nordicsemi.android.ble.ble_gatt_client.net

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 创建Retrofit提供API Service
 */
object RetrofitClient {

    private const val BASE_URL = "http://192.168.0.106:8080/"
//    private const val BASE_URL = "http://10.112.115.84:8080/"
//    private const val BASE_URL = "http://localhost:8080/"

    val okHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    val apiService = retrofit.create(ApiService::class.java)

}