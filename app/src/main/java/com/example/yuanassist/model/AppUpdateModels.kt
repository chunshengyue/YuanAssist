package com.example.yuanassist.model

import cn.bmob.v3.BmobObject

// 映射 update 表
class update : cn.bmob.v3.BmobObject() {
    var versionCode: Int = 0
    var versionName: String = ""
    var apkUrl: String = ""
    var releaseNotes: String = ""
}

// 映射 announcement 表
class announcement : cn.bmob.v3.BmobObject() {
    var version: Int = 0
    var title: String = ""
    var content: String = ""
}