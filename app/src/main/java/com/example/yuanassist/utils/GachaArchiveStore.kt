package com.example.yuanassist.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

enum class GachaGameVariant(
    val storageValue: String,
    val displayName: String,
) {
    RUYUAN("ruyuan", "如鸢"),
    DAIHAOYUAN("daihaoyuan", "代号鸢");

    companion object {
        fun fromStorageValue(value: String?): GachaGameVariant =
            entries.firstOrNull { it.storageValue == value } ?: RUYUAN
    }
}

enum class GachaAgentRarity(val displayName: String) {
    SECRET("绝密"),
    CONFIDENTIAL("机密"),
    HIDDEN("隐密"),
}

object GachaAgentRarityRegistry {
    private val confidentialAgents = setOf(
        "颜良", "陈登", "史子眇", "阿蝉", "文丑", "严白虎",
        "许攸", "陆绩", "公孙珊", "王异", "许曼", "小乔",
    )
    private val hiddenAgents = setOf(
        "周群", "蛾使", "雀使", "严颜", "崔烈", "伍丹", "李脱", "徐稺",
        "李真", "关靖", "甘缇", "毛玠", "高览", "蜂使", "眭固", "楼班",
        "山九", "鸢使", "杨阜", "第五天", "飞云", "绣球",
    )

    fun resolve(name: String): GachaAgentRarity = when (name.trim()) {
        in confidentialAgents -> GachaAgentRarity.CONFIDENTIAL
        in hiddenAgents -> GachaAgentRarity.HIDDEN
        else -> GachaAgentRarity.SECRET
    }

    fun isSecret(name: String): Boolean = resolve(name) == GachaAgentRarity.SECRET
}

data class GachaPoolDefinition(
    val id: String,
    val name: String,
    val gameVariant: GachaGameVariant,
    val upAgents: List<String>,
    val sortOrder: Int = 0,
    val countsTowardOffRate: Boolean = true,
    val coverUrl: String? = null,
    val status: String = "active",
)

object GachaPoolCatalog {
    const val WANGHOU_BINDE = "wanghou_binde"
    const val ZHOULANG_ZHIZHANG = "zhoulang_zhizhang"
    const val QUEYUE_LINGFENG = "daihao_001"
    const val CHENGZHI_YUNFU = "chengzhi_yunfu"
    const val RUYUAN_XIUYI_TIANXIA = "ruyuan_034"
    const val DAIHAOYUAN_XIUYI_TIANXIA = "daihao_052"

    private val pools = listOf(
        GachaPoolDefinition(
            id = WANGHOU_BINDE,
            name = "王侯秉德",
            gameVariant = GachaGameVariant.RUYUAN,
            upAgents = listOf("郭女王", "庞德"),
        ),
        GachaPoolDefinition("ruyuan_001", "却月凌风", GachaGameVariant.RUYUAN, listOf("凌统", "黄月英")),
        GachaPoolDefinition("ruyuan_002", "桓桓先征", GachaGameVariant.RUYUAN, listOf("曹丕", "吕布")),
        GachaPoolDefinition("ruyuan_003", "奉天华盖", GachaGameVariant.RUYUAN, listOf("黄盖", "董奉")),
        GachaPoolDefinition("ruyuan_004", "珠渊玉水", GachaGameVariant.RUYUAN, listOf("刘璋", "夏侯渊")),
        GachaPoolDefinition("ruyuan_005", "铁弦千钧", GachaGameVariant.RUYUAN, listOf("程普", "钟繇")),
        GachaPoolDefinition("ruyuan_006", "形谍成光", GachaGameVariant.RUYUAN, listOf("庞羲", "马腾")),
        GachaPoolDefinition("ruyuan_007", "白日昭只", GachaGameVariant.RUYUAN, listOf("张昭", "董白")),
        GachaPoolDefinition("ruyuan_008", "契阔谈宴", GachaGameVariant.RUYUAN, listOf("士燮", "程昱")),
        GachaPoolDefinition("ruyuan_009", "天弧封狼", GachaGameVariant.RUYUAN, listOf("张绣", "朱然")),
        GachaPoolDefinition("ruyuan_010", "蛇蟒之蛰", GachaGameVariant.RUYUAN, listOf("满宠", "刘繇")),
        GachaPoolDefinition("ruyuan_011", "英徽弥亮", GachaGameVariant.RUYUAN, listOf("诸葛亮", "司马徽")),
        GachaPoolDefinition("ruyuan_012", "风兴云蒸", GachaGameVariant.RUYUAN, listOf("蒯越", "祢衡")),
        GachaPoolDefinition("ruyuan_013", "谨司天英", GachaGameVariant.RUYUAN, listOf("董奉", "曹植")),
        GachaPoolDefinition("ruyuan_014", "腾陵张胆", GachaGameVariant.RUYUAN, listOf("凌统", "安期")),
        GachaPoolDefinition("ruyuan_015", "金相玉质", GachaGameVariant.RUYUAN, listOf("孔融", "黄月英")),
        GachaPoolDefinition("ruyuan_016", "云雨滂润", GachaGameVariant.RUYUAN, listOf("诸葛诞", "诸葛瑾")),
        GachaPoolDefinition("ruyuan_017", "壑林邀月", GachaGameVariant.RUYUAN, listOf("荀攸", "夏侯惇")),
        GachaPoolDefinition("ruyuan_018", "身藏北斗", GachaGameVariant.RUYUAN, listOf("郭解", "贾诩")),
        GachaPoolDefinition("ruyuan_019", "暮燕翻雷", GachaGameVariant.RUYUAN, listOf("虞翻", "张燕")),
        GachaPoolDefinition("ruyuan_020", "击金鸣鼓", GachaGameVariant.RUYUAN, listOf("甄宓", "甘宁")),
        GachaPoolDefinition("ruyuan_021", "彀弓衔刃", GachaGameVariant.RUYUAN, listOf("太史慈", "黄盖")),
        GachaPoolDefinition("ruyuan_022", "欺天罔地", GachaGameVariant.RUYUAN, listOf("戏学", "张邈")),
        GachaPoolDefinition("ruyuan_023", "织囊画诗", GachaGameVariant.RUYUAN, listOf("张闿", "张飞")),
        GachaPoolDefinition("ruyuan_024", "绮花隐豹", GachaGameVariant.RUYUAN, listOf("张郃", "刘豹")),
        GachaPoolDefinition("ruyuan_025", "形气复生", GachaGameVariant.RUYUAN, listOf("张修", "张鲁")),
        GachaPoolDefinition("ruyuan_026", "天道不逾", GachaGameVariant.RUYUAN, listOf("张仲景", "张角")),
        GachaPoolDefinition("ruyuan_027", "异才奇士", GachaGameVariant.RUYUAN, listOf("王粲", "郭嘉")),
        GachaPoolDefinition("ruyuan_028", "弹剑酿花", GachaGameVariant.RUYUAN, listOf("徐庶", "蔡琰")),
        GachaPoolDefinition("ruyuan_029", "弹香展骥", GachaGameVariant.RUYUAN, listOf("荀彧", "庞统")),
        GachaPoolDefinition("ruyuan_030", "长生之术", GachaGameVariant.RUYUAN, listOf("葛洪", "令狐茂")),
        GachaPoolDefinition("ruyuan_031", "九门磔攘", GachaGameVariant.RUYUAN, listOf("周瑜", "干吉")),
        GachaPoolDefinition("ruyuan_032", "动如雷霆", GachaGameVariant.RUYUAN, listOf("张辽", "马超")),
        GachaPoolDefinition("ruyuan_033", "弓箭江东", GachaGameVariant.RUYUAN, listOf("孙尚香", "孙权")),
        GachaPoolDefinition(
            id = RUYUAN_XIUYI_TIANXIA,
            name = "绣衣天下",
            gameVariant = GachaGameVariant.RUYUAN,
            upAgents = emptyList(),
            countsTowardOffRate = false,
        ),
        GachaPoolDefinition(
            id = ZHOULANG_ZHIZHANG,
            name = "周廊之璋",
            gameVariant = GachaGameVariant.DAIHAOYUAN,
            upAgents = listOf("周泰", "陈琳"),
        ),
        GachaPoolDefinition("daihao_001", "却月凌风", GachaGameVariant.DAIHAOYUAN, listOf("凌统", "黄月英")),
        GachaPoolDefinition(
            id = CHENGZHI_YUNFU,
            name = "诚之云孚",
            gameVariant = GachaGameVariant.DAIHAOYUAN,
            upAgents = listOf("赵云", "司马孚"),
        ),
        GachaPoolDefinition("daihao_002", "弓箭江东", GachaGameVariant.DAIHAOYUAN, listOf("孙尚香", "孙权")),
        GachaPoolDefinition("daihao_003", "鬼城之主", GachaGameVariant.DAIHAOYUAN, listOf("张修", "干吉")),
        GachaPoolDefinition("daihao_004", "异才奇士", GachaGameVariant.DAIHAOYUAN, listOf("王粲", "郭嘉")),
        GachaPoolDefinition("daihao_005", "万物化光", GachaGameVariant.DAIHAOYUAN, listOf("张辽", "张仲景")),
        GachaPoolDefinition("daihao_006", "山有扶苏", GachaGameVariant.DAIHAOYUAN, listOf("葛洪", "周瑜")),
        GachaPoolDefinition("daihao_007", "四方顺时", GachaGameVariant.DAIHAOYUAN, listOf("郭解", "华佗")),
        GachaPoolDefinition("daihao_008", "王佐之才", GachaGameVariant.DAIHAOYUAN, listOf("荀彧", "庞统")),
        GachaPoolDefinition("daihao_009", "坐照运筹", GachaGameVariant.DAIHAOYUAN, listOf("陆逊", "贾诩")),
        GachaPoolDefinition("daihao_010", "诗画同律", GachaGameVariant.DAIHAOYUAN, listOf("张飞", "蔡琰")),
        GachaPoolDefinition("daihao_011", "争光日月", GachaGameVariant.DAIHAOYUAN, listOf("张角", "夏侯惇")),
        GachaPoolDefinition("daihao_012", "度势攻心", GachaGameVariant.DAIHAOYUAN, listOf("吕蒙", "杨修")),
        GachaPoolDefinition("daihao_013", "出奇无穷", GachaGameVariant.DAIHAOYUAN, listOf("马超", "程昱")),
        GachaPoolDefinition("daihao_014", "天曲日术", GachaGameVariant.DAIHAOYUAN, listOf("太史慈", "张闿")),
        GachaPoolDefinition("daihao_015", "扬矢摧锋", GachaGameVariant.DAIHAOYUAN, listOf("张辽", "孙尚香")),
        GachaPoolDefinition("daihao_016", "绝艳惊鸿", GachaGameVariant.DAIHAOYUAN, listOf("张郃", "徐庶")),
        GachaPoolDefinition("daihao_017", "击金鸣鼓", GachaGameVariant.DAIHAOYUAN, listOf("甄宓", "甘宁")),
        GachaPoolDefinition("daihao_018", "秉数而一", GachaGameVariant.DAIHAOYUAN, listOf("孙权", "张仲景")),
        GachaPoolDefinition("daihao_019", "闻斯行诸", GachaGameVariant.DAIHAOYUAN, listOf("孔融", "张鲁")),
        GachaPoolDefinition("daihao_020", "天门启阖", GachaGameVariant.DAIHAOYUAN, listOf("黄月英", "张邈")),
        GachaPoolDefinition("daihao_021", "谨司天英", GachaGameVariant.DAIHAOYUAN, listOf("董奉", "曹植")),
        GachaPoolDefinition("daihao_022", "云雨滂润", GachaGameVariant.DAIHAOYUAN, listOf("诸葛诞", "诸葛瑾")),
        GachaPoolDefinition("daihao_023", "二柄刑德", GachaGameVariant.DAIHAOYUAN, listOf("荀攸", "满宠")),
        GachaPoolDefinition("daihao_024", "照以三光", GachaGameVariant.DAIHAOYUAN, listOf("张飞", "张闿")),
        GachaPoolDefinition("daihao_025", "腾陵张胆", GachaGameVariant.DAIHAOYUAN, listOf("凌统", "安期")),
        GachaPoolDefinition("daihao_026", "逐奔不远", GachaGameVariant.DAIHAOYUAN, listOf("刘繇", "戏学")),
        GachaPoolDefinition("daihao_027", "动如雷霆", GachaGameVariant.DAIHAOYUAN, listOf("张辽", "马超")),
        GachaPoolDefinition("daihao_028", "一体盈虚", GachaGameVariant.DAIHAOYUAN, listOf("令狐茂", "马腾")),
        GachaPoolDefinition("daihao_029", "搏势缚虬", GachaGameVariant.DAIHAOYUAN, listOf("黄盖", "朱然")),
        GachaPoolDefinition("daihao_030", "风兴云蒸", GachaGameVariant.DAIHAOYUAN, listOf("蒯越", "祢衡")),
        GachaPoolDefinition("daihao_031", "火中取梦", GachaGameVariant.DAIHAOYUAN, listOf("甄宓", "张郃")),
        GachaPoolDefinition("daihao_032", "深水逐鹿", GachaGameVariant.DAIHAOYUAN, listOf("张绣", "刘豹")),
        GachaPoolDefinition("daihao_033", "白日昭只", GachaGameVariant.DAIHAOYUAN, listOf("张昭", "董白")),
        GachaPoolDefinition("daihao_034", "隧象衔尾", GachaGameVariant.DAIHAOYUAN, listOf("程普", "士燮")),
        GachaPoolDefinition("daihao_035", "暮燕翻雷", GachaGameVariant.DAIHAOYUAN, listOf("虞翻", "张燕")),
        GachaPoolDefinition("daihao_036", "金相玉质", GachaGameVariant.DAIHAOYUAN, listOf("孔融", "黄月英")),
        GachaPoolDefinition("daihao_037", "渊鼓锽钟", GachaGameVariant.DAIHAOYUAN, listOf("钟繇", "夏侯渊")),
        GachaPoolDefinition("daihao_038", "劫烬灰飞", GachaGameVariant.DAIHAOYUAN, listOf("刘璋", "庞羲")),
        GachaPoolDefinition("daihao_039", "以奉嘉觞", GachaGameVariant.DAIHAOYUAN, listOf("周忠", "吕布")),
        GachaPoolDefinition("daihao_040", "大和丕应", GachaGameVariant.DAIHAOYUAN, listOf("简雍", "曹丕")),
        GachaPoolDefinition("daihao_041", "英徽弥亮", GachaGameVariant.DAIHAOYUAN, listOf("诸葛亮", "司马徽")),
        GachaPoolDefinition("daihao_042", "动静非我", GachaGameVariant.DAIHAOYUAN, listOf("蒯良", "郭女王")),
        GachaPoolDefinition("daihao_043", "持心离欲", GachaGameVariant.DAIHAOYUAN, listOf("陈群", "法正")),
        GachaPoolDefinition("daihao_044", "抱琴载酒", GachaGameVariant.DAIHAOYUAN, listOf("孙尚香", "周瑜")),
        GachaPoolDefinition("daihao_045", "洪匀玄机", GachaGameVariant.DAIHAOYUAN, listOf("葛洪", "张仲景")),
        GachaPoolDefinition("daihao_046", "珠玑琼玖", GachaGameVariant.DAIHAOYUAN, listOf("酆公玖", "酆公珠")),
        GachaPoolDefinition("daihao_047", "坐令千霄", GachaGameVariant.DAIHAOYUAN, listOf("庞德", "卢植")),
        GachaPoolDefinition("daihao_048", "影迹陈陈", GachaGameVariant.DAIHAOYUAN, listOf("陈纪", "陈应")),
        GachaPoolDefinition("daihao_049", "含明隐迹", GachaGameVariant.DAIHAOYUAN, listOf("孟获", "孙静")),
        GachaPoolDefinition("daihao_050", "松桥辅德", GachaGameVariant.DAIHAOYUAN, listOf("张松", "孙辅")),
        GachaPoolDefinition("daihao_051", "奉天华盖", GachaGameVariant.DAIHAOYUAN, listOf("黄盖", "董奉")),
        GachaPoolDefinition(
            id = DAIHAOYUAN_XIUYI_TIANXIA,
            name = "绣衣天下",
            gameVariant = GachaGameVariant.DAIHAOYUAN,
            upAgents = emptyList(),
            countsTowardOffRate = false,
        ),
    ).map { pool -> pool.copy(sortOrder = builtInSortOrder(pool)) }
    private var cloudPools: List<GachaPoolDefinition> = emptyList()

    private fun builtInSortOrder(pool: GachaPoolDefinition): Int = when {
        pool.id == RUYUAN_XIUYI_TIANXIA || pool.id == DAIHAOYUAN_XIUYI_TIANXIA -> 100000
        pool.id == WANGHOU_BINDE -> 340
        pool.id.startsWith("ruyuan_") -> {
            val number = pool.id.removePrefix("ruyuan_").toIntOrNull() ?: return 0
            (34 - number) * 10
        }
        pool.id == ZHOULANG_ZHIZHANG -> 530
        pool.id == QUEYUE_LINGFENG -> 520
        pool.id == CHENGZHI_YUNFU -> 510
        pool.id.startsWith("daihao_") -> {
            val number = pool.id.removePrefix("daihao_").toIntOrNull() ?: return 0
            (number - 1) * 10
        }
        else -> 0
    }

    fun forVariant(variant: GachaGameVariant): List<GachaPoolDefinition> {
        return allPools()
            .filter { it.gameVariant == variant && it.status == "active" }
            .sortedWith(compareByDescending<GachaPoolDefinition> { it.sortOrder }.thenBy { it.id })
    }

    fun find(poolId: String?): GachaPoolDefinition? = allPools().firstOrNull { it.id == poolId }

    fun setCloudPools(pools: List<GachaPoolDefinition>) {
        cloudPools = pools.distinctBy { it.id }
    }

    private fun allPools(): List<GachaPoolDefinition> {
        val merged = pools.associateBy { it.id }.toMutableMap()
        cloudPools.forEach { cloud ->
            val builtIn = merged[cloud.id]
            merged[cloud.id] = builtIn?.copy(
                name = cloud.name,
                gameVariant = cloud.gameVariant,
                upAgents = cloud.upAgents,
                sortOrder = cloud.sortOrder,
                coverUrl = cloud.coverUrl,
                status = cloud.status,
            ) ?: cloud
        }
        return merged.values.toList()
    }
}

data class GachaPoolCatalogRefreshResult(
    val addedCount: Int,
    val updatedCount: Int,
)

object GachaPoolCatalogStore {
    private const val FILE_NAME = "gacha_pool_catalog.json"
    private val gson = Gson()

    fun load(context: Context): List<GachaPoolDefinition> {
        val pools = read(context)
        GachaPoolCatalog.setCloudPools(pools)
        return pools
    }

    fun merge(
        context: Context,
        incomingPools: List<GachaPoolDefinition>,
    ): GachaPoolCatalogRefreshResult {
        val existing = read(context)
        val existingById = existing.associateBy { it.id }
        val normalized = incomingPools
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && !it.coverUrl.isNullOrBlank() }
            .distinctBy { it.id }
        val merged = (existingById + normalized.associateBy { it.id }).values.toList()
        write(context, merged)
        GachaPoolCatalog.setCloudPools(merged)
        return GachaPoolCatalogRefreshResult(
            addedCount = normalized.count { it.id !in existingById },
            updatedCount = normalized.count { it.id in existingById },
        )
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): List<GachaPoolDefinition> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<GachaPoolDefinition>>(
                file.readText(Charsets.UTF_8),
                object : TypeToken<List<GachaPoolDefinition>>() {}.type,
            ).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, pools: List<GachaPoolDefinition>) {
        file(context).writeText(gson.toJson(pools), Charsets.UTF_8)
    }
}

data class GachaPoolProgress(
    val poolId: String,
    val totalPulls: Int = 0,
    val secretCount: Int = 0,
    val confidentialCount: Int = 0,
    val nonUpSecretCount: Int = 0,
    val currentPity: Int = 0,
    val secretAgentNames: List<String>? = emptyList(),
    val secretPullCounts: List<Int>? = emptyList(),
    val upAgentCounts: Map<String, Int>? = emptyMap(),
    val hasManualSecretEntries: Boolean = false,
    val remainingPity: Int? = null,
)

data class GachaArchive(
    val id: String,
    val name: String,
    val gameVariantValue: String,
    val createdAt: Long,
    val updatedAt: Long,
    val poolProgresses: List<GachaPoolProgress> = emptyList(),
) {
    val gameVariant: GachaGameVariant
        get() = GachaGameVariant.fromStorageValue(gameVariantValue)
}

data class GachaArchiveSummary(
    val totalPulls: Int,
    val averageSecretPulls: Int?,
    val nonUpSecretRatio: Int?,
    val mostDrawnSecretAgent: GachaAgentFrequency?,
    val mostNonUpSecretAgent: GachaAgentFrequency?,
)

data class GachaAgentFrequency(
    val agentNames: List<String>,
    val count: Int,
)

private data class GachaArchiveMeta(
    val archives: List<GachaArchive> = emptyList(),
)

object GachaArchiveStore {
    private const val DIR_NAME = "gacha_records"
    private const val ARCHIVES_FILE_NAME = "archives.json"
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SELECTED_ARCHIVE_ID = "selected_gacha_archive_id"
    private val fallbackSecretPullCounts = listOf(12, 34, 25, 40, 28)
    private val gson = Gson()

    private fun rootDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun archivesFile(context: Context): File = File(rootDir(context), ARCHIVES_FILE_NAME)

    fun listArchives(context: Context): List<GachaArchive> {
        ensureInitialized(context)
        return readMeta(context).archives.sortedBy { it.createdAt }
    }

    fun getSelectedArchive(context: Context): GachaArchive {
        val archives = listArchives(context)
        val storedId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ARCHIVE_ID, null)
        val selected = archives.firstOrNull { it.id == storedId } ?: archives.first()
        if (storedId != selected.id) setSelectedArchiveId(context, selected.id)
        return selected
    }

    fun setSelectedArchiveId(context: Context, archiveId: String) {
        val selectedId = listArchives(context).firstOrNull { it.id == archiveId }?.id
            ?: listArchives(context).first().id
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_ARCHIVE_ID, selectedId)
            .apply()
    }

    fun createArchive(
        context: Context,
        name: String,
        gameVariant: GachaGameVariant,
    ): GachaArchive {
        ensureInitialized(context)
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "请输入存档名称" }
        require(normalizedName.length <= 20) { "存档名称不能超过 20 个字" }

        val archives = listArchives(context)
        require(archives.none { it.name == normalizedName }) { "已存在同名存档" }

        val now = System.currentTimeMillis()
        val archive = GachaArchive(
            id = "gacha_${now}_${System.nanoTime()}",
            name = normalizedName,
            gameVariantValue = gameVariant.storageValue,
            createdAt = now,
            updatedAt = now,
        )
        saveMeta(context, archives + archive)
        setSelectedArchiveId(context, archive.id)
        return archive
    }

    fun deleteArchive(context: Context, archiveId: String) {
        val archives = listArchives(context)
        require(archives.size > 1) { "至少保留一个存档" }
        require(archives.any { it.id == archiveId }) { "未找到当前存档" }
        saveMeta(context, archives.filterNot { it.id == archiveId })
        val selectedId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ARCHIVE_ID, null)
        if (selectedId == archiveId) {
            setSelectedArchiveId(context, listArchives(context).first().id)
        }
    }

    fun replaceArchive(context: Context, archive: GachaArchive) {
        require(archive.id.isNotBlank()) { "云端存档无效" }
        require(archive.name.isNotBlank()) { "云端存档名称无效" }
        val archives = listArchives(context)
        require(archives.any { it.id == archive.id }) { "当前本地存档不存在" }
        val normalized = archive.copy(updatedAt = System.currentTimeMillis())
        saveMeta(context, archives.map { existing ->
            if (existing.id == normalized.id) normalized else existing
        })
        setSelectedArchiveId(context, normalized.id)
    }

    fun progressFor(archive: GachaArchive, poolId: String): GachaPoolProgress =
        archive.poolProgresses.firstOrNull { it.poolId == poolId }
            ?: GachaPoolProgress(poolId = poolId)

    fun reconciledProgress(
        pool: GachaPoolDefinition,
        progress: GachaPoolProgress,
    ): GachaPoolProgress {
        val names = progress.secretAgentNames.orEmpty()
        val secretEntries = names.mapIndexedNotNull { index, name ->
            if (GachaAgentRarityRegistry.isSecret(name)) {
                index to name
            } else {
                null
            }
        }
        val secretPullTotal = secretEntries.sumOf { (index, _) ->
            secretPullCountAt(progress, index)
        }
        val remainingPity = remainingPityFor(progress)
        return progress.copy(
            totalPulls = (secretPullTotal + 40 - remainingPity).coerceAtLeast(0),
            secretCount = secretEntries.size,
            nonUpSecretCount = if (pool.countsTowardOffRate) {
                secretEntries.count { (_, name) -> name !in pool.upAgents }
            } else {
                0
            },
        )
    }

    fun secretPullCountAt(progress: GachaPoolProgress, index: Int): Int {
        progress.secretPullCounts.orEmpty().getOrNull(index)?.let { return it }
        if (progress.hasManualSecretEntries && progress.secretCount == 1 && index == 0) {
            return progress.totalPulls.coerceIn(0, 40)
        }
        return fallbackSecretPullCounts[index % fallbackSecretPullCounts.size]
    }

    fun remainingPityFor(progress: GachaPoolProgress): Int =
        progress.remainingPity ?: (40 - progress.currentPity.coerceIn(0, 40))

    fun updateRemainingPity(
        context: Context,
        archiveId: String,
        poolId: String,
        remainingPity: Int,
    ): GachaPoolProgress {
        require(remainingPity in 0..40) { "距离保底需在 0 到 40 抽之间" }
        val archives = listArchives(context)
        val archive = archives.firstOrNull { it.id == archiveId }
            ?: throw IllegalArgumentException("未找到当前存档")
        val pool = GachaPoolCatalog.find(poolId)
            ?: throw IllegalArgumentException("未找到当前卡池")
        val current = reconciledProgress(pool, progressFor(archive, poolId))
        val secretPullTotal = (current.totalPulls - (40 - remainingPityFor(current)))
            .coerceAtLeast(0)
        val updated = current.copy(
            totalPulls = secretPullTotal + 40 - remainingPity,
            currentPity = 40 - remainingPity,
            remainingPity = remainingPity,
        )
        saveProgress(context, archives, archive, updated)
        return updated
    }

    fun summaryFor(archive: GachaArchive): GachaArchiveSummary {
        val validProgresses = archive.poolProgresses.mapNotNull { progress ->
            GachaPoolCatalog.find(progress.poolId)
                ?.takeIf { it.gameVariant == archive.gameVariant && it.status == "active" }
                ?.let { pool -> pool to reconciledProgress(pool, progress) }
        }
        val secretAgentNames = validProgresses.flatMap { progress ->
            progress.second.secretAgentNames.orEmpty().filter(GachaAgentRarityRegistry::isSecret)
        }
        val nonUpSecretAgentNames = validProgresses.filter { (pool, _) ->
            pool.countsTowardOffRate
        }.flatMap { (pool, progress) ->
            progress.secretAgentNames.orEmpty().filter { name ->
                GachaAgentRarityRegistry.isSecret(name) && name !in pool.upAgents
            }
        }
        val offRateProgresses = validProgresses.filter { (pool, _) ->
            pool.countsTowardOffRate
        }
        return GachaArchiveSummary(
            totalPulls = validProgresses.sumOf { it.second.totalPulls },
            averageSecretPulls = validProgresses.sumOf { it.second.secretCount }
                .takeIf { it > 0 }
                ?.let { validProgresses.sumOf { progress -> progress.second.totalPulls } / it },
            nonUpSecretRatio = offRateProgresses.sumOf { it.second.secretCount }
                .takeIf { it > 0 }
                ?.let { offRateProgresses.sumOf { progress -> progress.second.nonUpSecretCount } * 100 / it },
            mostDrawnSecretAgent = highestFrequency(secretAgentNames),
            mostNonUpSecretAgent = highestFrequency(nonUpSecretAgentNames),
        )
    }

    private fun highestFrequency(agentNames: List<String>): GachaAgentFrequency? {
        val counts = agentNames.groupingBy { it }.eachCount()
        val highestCount = counts.values.maxOrNull() ?: return null
        return GachaAgentFrequency(
            agentNames = counts
                .filterValues { it == highestCount }
                .keys
                .sorted(),
            count = highestCount,
        )
    }

    fun appendManualSecret(
        context: Context,
        archiveId: String,
        poolId: String,
        agentName: String,
        pulls: Int,
    ): GachaPoolProgress {
        require(pulls in 1..40) { "抽数需在 1 到 40 之间" }
        require(GachaAgentRarityRegistry.isSecret(agentName)) { "手动录入仅支持绝密密探" }

        val archives = listArchives(context)
        val archive = archives.firstOrNull { it.id == archiveId }
            ?: throw IllegalArgumentException("未找到当前存档")
        val pool = GachaPoolCatalog.find(poolId)
            ?: throw IllegalArgumentException("未找到当前卡池")
        require(pool.gameVariant == archive.gameVariant) { "当前存档不包含该卡池" }

        val current = reconciledProgress(pool, progressFor(archive, poolId))
        val isUpAgent = agentName in pool.upAgents
        val existingSecretNames = current.secretAgentNames.orEmpty()
        val updatedCounts = current.upAgentCounts.orEmpty().toMutableMap().apply {
            if (isUpAgent) put(agentName, (this[agentName] ?: 0) + 1)
        }
        val updated = current.copy(
            totalPulls = (current.totalPulls - (40 - remainingPityFor(current))).coerceAtLeast(0) + pulls,
            secretCount = current.secretCount + 1,
            nonUpSecretCount = current.nonUpSecretCount + if (pool.countsTowardOffRate && !isUpAgent) 1 else 0,
            currentPity = 0,
            secretAgentNames = existingSecretNames + agentName,
            secretPullCounts = existingSecretNames.indices.map { index ->
                secretPullCountAt(current, index)
            } + pulls,
            upAgentCounts = updatedCounts,
            hasManualSecretEntries = true,
            remainingPity = 40,
        )
        saveProgress(context, archives, archive, updated)
        return updated
    }

    fun deleteSecretEntry(
        context: Context,
        archiveId: String,
        poolId: String,
        entryIndex: Int,
    ): GachaPoolProgress {
        val archives = listArchives(context)
        val archive = archives.firstOrNull { it.id == archiveId }
            ?: throw IllegalArgumentException("未找到当前存档")
        val pool = GachaPoolCatalog.find(poolId)
            ?: throw IllegalArgumentException("未找到当前卡池")
        val current = reconciledProgress(pool, progressFor(archive, poolId))
        val names = current.secretAgentNames.orEmpty()
        require(entryIndex in names.indices) { "未找到要删除的绝密记录" }

        val removedPulls = secretPullCountAt(current, entryIndex)
        val updatedNames = names.toMutableList().apply { removeAt(entryIndex) }
        val resolvedPulls = names.indices.map { index -> secretPullCountAt(current, index) }
            .toMutableList()
            .apply { removeAt(entryIndex) }
        val remainingSecretNames = updatedNames.filter(GachaAgentRarityRegistry::isSecret)
        val recalculatedUpCounts = remainingSecretNames
            .filter { it in pool.upAgents }
            .groupingBy { it }
            .eachCount()
        val updated = current.copy(
            totalPulls = (current.totalPulls - removedPulls).coerceAtLeast(0),
            secretCount = remainingSecretNames.size,
            nonUpSecretCount = if (pool.countsTowardOffRate) {
                remainingSecretNames.count { it !in pool.upAgents }
            } else {
                0
            },
            secretAgentNames = updatedNames,
            secretPullCounts = resolvedPulls,
            upAgentCounts = recalculatedUpCounts,
            hasManualSecretEntries = current.hasManualSecretEntries && updatedNames.isNotEmpty(),
        )
        saveProgress(context, archives, archive, updated)
        return updated
    }

    private fun saveProgress(
        context: Context,
        archives: List<GachaArchive>,
        archive: GachaArchive,
        progress: GachaPoolProgress,
    ) {
        val updatedArchive = archive.copy(
            updatedAt = System.currentTimeMillis(),
            poolProgresses = archive.poolProgresses
                .filterNot { it.poolId == progress.poolId } + progress,
        )
        saveMeta(context, archives.map { if (it.id == archive.id) updatedArchive else it })
        setSelectedArchiveId(context, archive.id)
    }

    private fun ensureInitialized(context: Context) {
        val file = archivesFile(context)
        if (file.exists() && readMeta(context).archives.isNotEmpty()) return

        val now = System.currentTimeMillis()
        val defaultArchive = GachaArchive(
            id = "default_ruyuan",
            name = "默认如鸢存档",
            gameVariantValue = GachaGameVariant.RUYUAN.storageValue,
            createdAt = now,
            updatedAt = now,
        )
        saveMeta(context, listOf(defaultArchive))
    }

    private fun readMeta(context: Context): GachaArchiveMeta {
        val file = archivesFile(context)
        if (!file.exists()) return GachaArchiveMeta()
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), GachaArchiveMeta::class.java) ?: GachaArchiveMeta()
        }.getOrDefault(GachaArchiveMeta())
    }

    private fun saveMeta(context: Context, archives: List<GachaArchive>) {
        archivesFile(context).writeText(
            gson.toJson(GachaArchiveMeta(archives = archives.sortedBy { it.createdAt })),
            Charsets.UTF_8,
        )
    }
}
