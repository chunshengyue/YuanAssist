package com.example.yuanassist.network

import com.example.yuanassist.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ImageUploadApiService {
    // 采用 multipart/form-data 格式上传
    @Multipart
    @POST("api/v1.php")
    fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("outputFormat") outputFormat: RequestBody
    ): Call<UploadResponse>
}

object ImageUploadClient {
    private const val BASE_URL = "https://img.scdn.io/"

    val api: ImageUploadApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImageUploadApiService::class.java)
    }
}