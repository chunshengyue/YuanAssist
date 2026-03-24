package com.example.yuanassist.model

data class UploadResponse(
    val success: Boolean,
    val url: String?,
    val message: String?,
    val data: ImageData?
)

data class ImageData(
    val filename: String,
    val original_size: Long,
    val compressed_size: Long,
    val compression_ratio: Double // 🟢 改成 Double 就完美兼容了
)