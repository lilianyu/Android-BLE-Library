package com.techvll.android.detector.net

import com.techvll.android.detector.entity.PackageInfo
import com.techvll.android.detector.entity.User
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("evd/user/{id}")
    suspend fun getUserById(@Path("id") id: Long): ResultData<User>

    @GET("checkNewVersion")
    suspend fun checkNewVersion(@Query("userId") userId: Long?,
                                @Query("macAddress") macAddress: String,
                                @Query("hwVersion") hwVersion: Long,
                                @Query("swVersion") swVersion: Long): ResultData<PackageInfo>
}