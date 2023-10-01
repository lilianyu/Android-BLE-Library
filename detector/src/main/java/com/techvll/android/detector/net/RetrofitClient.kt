package com.techvll.android.detector.net

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 创建Retrofit提供API Service
 */
object RetrofitClient {
    private const val BASE_URL = "http://58.87.103.191:8081/"
//    private const val BASE_URL = "http://123.57.28.176:8081/"
//    private const val BASE_URL = "http://192.168.0.106:8081/"
//    private const val BASE_URL = "http://10.112.115.84:8080/"
//    private const val BASE_URL = "http://localhost:8080/"

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
//        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    val apiService = retrofit.create(ApiService::class.java)

}