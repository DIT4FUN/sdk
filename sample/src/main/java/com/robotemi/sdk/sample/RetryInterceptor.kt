package com.robotemi.sdk.sample

// RetryInterceptor.kt
import android.os.SystemClock
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

//网络拦截器,网络重试机制
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var currentRequest = chain.request()
        var retryCount = 0

        while (true) {
            try {
                val response = chain.proceed(currentRequest)
                if (response.code !in 500..599 || retryCount >= maxRetries) {
                    return response
                }
            } catch (e: IOException) {
                if (e is SocketTimeoutException) {
                    Log.w("Retry", "Timeout retry $retryCount")
                }
                if (retryCount >= maxRetries) throw e
            }

            // 指数退避策略
            val backoffMillis = 1000L * (2L shl retryCount)
            SystemClock.sleep(backoffMillis)
            retryCount++

            // 重建请求（重要！）
            currentRequest = currentRequest.newBuilder()
                .header("Retry-Count", retryCount.toString())
                .build()
        }
    }
}
