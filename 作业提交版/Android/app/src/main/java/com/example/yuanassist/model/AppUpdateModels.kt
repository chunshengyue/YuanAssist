package com.example.yuanassist.model

class update {
    var versionCode: Int = 0
    var versionName: String = ""
    var apkUrl: String = ""
    var releaseNotes: String = ""
}

data class GiteeUpdatePayload(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = ""
) {
    fun toUpdateModel(): update {
        return update().apply {
            this.versionCode = this@GiteeUpdatePayload.versionCode
            this.versionName = this@GiteeUpdatePayload.versionName
            this.apkUrl = this@GiteeUpdatePayload.apkUrl
            this.releaseNotes = this@GiteeUpdatePayload.releaseNotes
        }
    }
}

class announcement : SupabaseRecord() {
    var version: Int = 0
    var title: String = ""
    var content: String = ""
}
