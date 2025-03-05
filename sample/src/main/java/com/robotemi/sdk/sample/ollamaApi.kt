package com.robotemi.sdk.sample

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Streaming

interface OllamaApi {
    @POST("/api/chat")
    @Streaming
    fun sendMessage(@Body request:ChatRequest): Call<ResponseBody>

}

data class ChatRequest(
    val model: String,
    val messages: List<Message>
)

data class ChatResponse(
    val model: String,
    val created_at: String,
    val message: Message,
    val done: Boolean,
    val total_duration: Long,
    val load_duration: Long,
    val prompt_eval_count: Int,
    val prompt_eval_duration: Long,
    val eval_count: Int,
    val eval_duration: Long
)

data class Message(
    val role: String,
    var content: String,
    val images: List<String>? = null
)
