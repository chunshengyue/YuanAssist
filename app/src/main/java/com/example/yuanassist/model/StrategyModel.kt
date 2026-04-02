package com.example.yuanassist.model

import cn.bmob.v3.BmobObject
import cn.bmob.v3.BmobUser
import com.example.yuanassist.ui.UploadTurnItem

const val STRATEGY_VISIBLE_HIDDEN = 0
const val STRATEGY_VISIBLE_PUBLIC = 1
const val STRATEGY_GAME_DAIHAOYUAN = 0
const val STRATEGY_GAME_RUYUAN = 1

/**
 * 1. 扩展用户表 (Bmob自带_User表)
 */
class MyUser : BmobUser() {
    var nickname: String = ""
    var avatarUrl: String = ""
}

/**
 * 2. 攻略列表页 (对应 Bmob 里的 strategy_list 表)
 */
class strategy_detail : BmobObject() {
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

class strategy_favorite : BmobObject() {
    var user: MyUser? = null
    var strategy: strategy_detail? = null
    var uniqueKey: String = ""
}
/**
 * 4. 预览与传输用的本地数据包 (不存数据库，仅 Intent 传值用)
 */
