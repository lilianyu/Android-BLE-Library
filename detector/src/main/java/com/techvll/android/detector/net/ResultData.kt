package com.techvll.android.detector.net

data class ResultData<T>(
    val code: Int,
    val message: String,
    val data: T
)