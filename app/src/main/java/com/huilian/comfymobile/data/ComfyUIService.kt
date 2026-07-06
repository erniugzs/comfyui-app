package com.huilian.comfymobile.data

import com.google.gson.JsonObject
import com.huilian.comfymobile.data.models.PromptRequest
import com.huilian.comfymobile.data.models.PromptResponse
import com.huilian.comfymobile.data.models.QueueStatusResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ComfyUIService {
    @POST("api/prompt")
    suspend fun postPrompt(@Body request: PromptRequest): Response<PromptResponse>

    @GET("api/queue")
    suspend fun getQueue(): Response<QueueStatusResponse>

    @DELETE("api/queue")
    suspend fun clearQueue(): Response<JsonObject>

    @POST("api/interrupt")
    suspend fun interruptQueue(): Response<JsonObject>

    @GET("api/history")
    suspend fun getHistory(): Response<JsonObject>

    @GET("api/history")
    suspend fun getHistory(@Query("max_items") maxItems: Int): Response<JsonObject>

    @GET("api/history/{promptId}")
    suspend fun getHistoryById(@Path("promptId") promptId: String): Response<JsonObject>

    @GET("api/object_info/{nodeType}")
    suspend fun getObjectInfo(@Path("nodeType") nodeType: String): Response<JsonObject>

    @GET("api/object_info")
    suspend fun getAllObjectInfo(): Response<JsonObject>

    @GET("api/view")
    suspend fun getView(
        @Query("filename") filename: String,
        @Query("subfolder") subfolder: String? = null,
        @Query("type") type: String = "output"
    ): Response<ResponseBody>

    @GET("api/system_stats")
    suspend fun getSystemStats(): Response<JsonObject>

    @GET("api/embeddings")
    suspend fun getEmbeddings(): Response<JsonObject>

    @GET("api/extensions")
    suspend fun getExtensions(): Response<JsonObject>

    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Query("overwrite") overwrite: Boolean = true
    ): Response<JsonObject>

    @POST("workflow/convert")
    suspend fun convertWorkflow(@Body body: JsonObject): Response<JsonObject>

    // 工作流文件管理 - 原始 App 使用 /userdata 接口
    @GET("userdata")
    suspend fun getWorkflowList(@Query("dir") dir: String): Response<List<String>>

    @GET("userdata/{file}")
    suspend fun getWorkflow(@Path("file") file: String): Response<JsonObject>

    @GET("userdata")
    suspend fun getGalleryImages(@Query("dir") dir: String): Response<List<String>>

    @POST("api/interrupt")
    suspend fun interruptTask(): Response<JsonObject>

    @DELETE("userdata/{file}")
    suspend fun deleteFile(@Path("file") file: String): Response<JsonObject>
}
