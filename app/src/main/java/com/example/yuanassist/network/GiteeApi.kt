package com.example.yuanassist.network

import com.example.yuanassist.model.DdlItem
import com.example.yuanassist.model.StrategyItem
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface GiteeApiService {
    // 获取攻略列表 (原有)
    @GET("chunshengyue/yuan-assist-data/raw/master/strategy_list.json")
    fun getStrategyList(): Call<List<StrategyItem>>

    // 🔴 获取 DDL 列表 (新增)
    @GET("chunshengyue/yuan-assist-data/raw/master/ddl_list.json")
    fun getDdlList(): Call<List<DdlItem>>
}

object NetworkClient {
    private const val BASE_URL = "https://gitee.com/"

    val api: GiteeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiteeApiService::class.java)
    }
}