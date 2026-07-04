package com.example.yuanassist.utils

const val FEEDBACK_ADMIN_DEVICE_ID = "815e9c7c33fa662e"

fun isFeedbackAdminDevice(deviceId: String?): Boolean {
    return deviceId?.trim() == FEEDBACK_ADMIN_DEVICE_ID
}

fun firstFeedbackImageUrl(imageUrls: String): String {
    return imageUrls
        .split(Regex("[\\r\\n,;|]+"))
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
