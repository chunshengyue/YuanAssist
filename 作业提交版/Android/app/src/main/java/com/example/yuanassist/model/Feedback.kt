package com.example.yuanassist.model

const val ISSUE_FEEDBACK_STATUS_PENDING = 0
const val ISSUE_FEEDBACK_STATUS_REPLIED = 1

class issue_feedback : SupabaseRecord() {
    var deviceId: String = ""
    var user: MyUser? = null
    var description: String = ""
    var logContent: String = ""
    var imageUrls: String = ""
    var reply: String = ""
    var status: Int = ISSUE_FEEDBACK_STATUS_PENDING
}
