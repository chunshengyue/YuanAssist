package com.example.yuanassist.network

import com.example.yuanassist.model.UploadResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

object ImageUploader {

    // 🔴 这里的 uploadToPublicHost 必须定义在 object 内部或作为全局函数
    fun uploadToPublicHost(
        imageFile: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
        val formatBody = "webp".toRequestBody("text/plain".toMediaTypeOrNull())

        ImageUploadClient.api.uploadImage(body, formatBody).enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                val uploadRes = response.body()
                if (response.isSuccessful && uploadRes != null && uploadRes.success) {
                    uploadRes.url?.let { onSuccess(it) }
                } else {
                    onError(uploadRes?.message ?: "上传失败: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                onError("网络异常: ${t.message}")
            }
        })
    }
}