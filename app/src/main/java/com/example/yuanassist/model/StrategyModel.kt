package com.example.yuanassist.model

import com.example.yuanassist.ui.UploadTurnItem

const val STRATEGY_VISIBLE_HIDDEN = 0
const val STRATEGY_VISIBLE_PUBLIC = 1
const val STRATEGY_GAME_DAIHAOYUAN = 0
const val STRATEGY_GAME_RUYUAN = 1
const val STRATEGY_MESSAGE_TYPE_STRATEGY_COMMENT = 1
const val STRATEGY_MESSAGE_TYPE_COMMENT_REPLY = 2

open class SupabaseRecord {
    var objectId: String? = null
    var createdAt: String? = null
    var updatedAt: String? = null
}

class MyUser : SupabaseRecord() {
    var username: String = ""
    var nickname: String = ""
    var avatarUrl: String = ""
    var deviceId: String = ""
}

class strategy_detail : SupabaseRecord() {
    var title: String = ""
    var content: String = ""        // 图文说明文本
    var scriptContent: String = ""   // 脚本指令大文本
    var config: String = ""          // 参数 JSON (ScriptConfigJson)
    var instructions: String = ""    // 附加指令 JSON (InstructionJson)
    var strategyImage: String = ""
    var agents: String = ""          // 密探摘要，如 "孙尚香、颜良..."
    var coverUrl: String = ""        // 列表封面图
    var originalPostUrl: String = ""
    var agentType: Int = 0           // 0:选密探, 1:截图, 2:文字
    var agentSelection: String = ""  // 选中的5个密探JSON
    var agentImageUrl: String = ""   // 阵容截图URL
    var agentTextDesc: String = ""
    var visible: Int? = null         // 由 Bmob 后端控制：1=公开，0=仅我的发布可见
    var ruyuan: Int? = null          // 由 Bmob 后端控制：1=如鸢，0=代号鸢

    var viewCount: Int? = 0
    var favoriteCount: Int? = 0
    var author: MyUser? = null
}

class strategy_favorite : SupabaseRecord() {
    var user: MyUser? = null
    var strategy: strategy_detail? = null
    var uniqueKey: String = ""
}

class strategy_comment : SupabaseRecord() {
    var strategy: strategy_detail? = null
    var user: MyUser? = null
    var content: String = ""
    var replyToComment: strategy_comment? = null
    var replyToUser: MyUser? = null
    var replyToUserName: String = ""
}

class strategy_message : SupabaseRecord() {
    var recipient: MyUser? = null
    var sender: MyUser? = null
    var strategy: strategy_detail? = null
    var comment: strategy_comment? = null
    var type: Int = 0
    var contentSnapshot: String = ""
    var isRead: Boolean = false
}

class cloud_daily_script : SupabaseRecord() {
    var title: String = ""
    var description: String = ""
    var tags: String = ""
    var guideImages: String = "[]"
    var bundlePath: String = ""
    var bundleSize: Long = 0L
    var taskCount: Int = 0
    var downloadCount: Int = 0
    var status: String = "published"
    var author: MyUser? = null
}

/**
 * 4. 预览与传输用的本地数据包 (不存数据库，仅 Intent 传值用)
 */
