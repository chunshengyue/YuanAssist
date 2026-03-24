package com.example.yuanassist.model

import cn.bmob.v3.BmobObject

// 繼承 BmobObject 的類別會自動對應到 Bmob 雲端的一張資料表
class Feedback : BmobObject() {
    var title: String? = null
    var content: String? = null
    var deviceId: String? = null
}