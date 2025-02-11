package com.robotemi.sdk.sample

// RetryInterceptor.kt
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response? = null
        while (attempt < maxRetries) {
            try {
                response = chain.proceed(chain.request())
                if (response.isSuccessful) {
                    return response
                }
            } catch (e: IOException) {
                Log.e("RetryInterceptor", "Attempt $attempt failed: ${e.message}")
            }
            attempt++
        }
        return response ?: throw RuntimeException("Failed after $maxRetries attempts")
    }
}
