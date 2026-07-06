package com.huilian.comfymobile.data

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var retrofit: Retrofit? = null
    private var comfyUIService: ComfyUIService? = null
    private var currentBaseUrl: String = ""

    private val gson by lazy {
        GsonBuilder().setLenient().create()
    }

    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request()
        chain.proceed(
            request.newBuilder()
                .header("Accept", "*/*")
                .header("Referer", currentBaseUrl)
                .header("Connection", "keep-alive")
                .header("Cache-Control", "max-age=0")
                .header("User-Agent", "Mozilla/5.0 (Android) ComfyUI Android Client")
                .method(request.method, request.body)
                .build()
        )
    }

    fun getService(baseUrl: String): ComfyUIService {
        if (retrofit == null || currentBaseUrl != baseUrl || comfyUIService == null) {
            currentBaseUrl = baseUrl
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(headerInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()

            comfyUIService = retrofit!!.create(ComfyUIService::class.java)
        }
        return comfyUIService!!
    }

    fun reset() {
        retrofit = null
        comfyUIService = null
        currentBaseUrl = ""
    }
}
