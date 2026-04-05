package plus.maa.backend.service.level

import plus.maa.backend.repository.entity.ArkLevel

/**
 * 手工维护的关卡列表。
 */
object ManualArkLevels {
    private data class Entry(
        val catOne: String,
        val catTwo: String? = null,
        val catThree: String,
    )

    private val entries = listOf(
        Entry("主线",catThree = "其他"),
        Entry("洞窟",catTwo="左", catThree = "阿茂灵花手作(25.10)"),
        Entry("洞窟",catTwo="右", catThree = "自作主璋(25.10)"),
        Entry("兰台", catThree = "一期"),
        Entry("兰台", catThree = "二期"),
        Entry("兰台", catThree = "三期"),
        Entry("兰台", catThree = "四期"),
        Entry("兰台", catThree = "五期"),

        Entry("地宫", catThree = "浮白与离光"),
        Entry("地宫", catThree = "金窗绣户"),
        Entry("地宫", catThree = "乌飞"),
        Entry("地宫", catThree = "若书之说"),
        Entry("地宫", catThree = "乌云白云"),
        Entry("地宫", catThree = "狐截尾"),
        Entry("地宫", catThree = "神游"),
        Entry("地宫", catThree = "紫藤醉日"),
        Entry("地宫", catThree = "却扇歌"),
        Entry("地宫", catThree = "极乐之宴"),
        Entry("地宫", catThree = "师子狻猊"),
        Entry("地宫", catThree = "璃魂月魄"),
        Entry("地宫", catThree = "湖心之梦"),
        Entry("地宫", catThree = "围城"),
        Entry("地宫", catThree = "盛宴"),
        Entry("地宫", catThree = "灯之国"),

        Entry("家具", catThree = "春山重重"),
        Entry("家具", catThree = "儿童劫"),
        Entry("家具", catThree = "海里捞捞"),
        Entry("家具", catThree = "隆地冬"),
        Entry("家具", catThree = "游春集"),
        Entry("家具", catThree = "灯火夜街"),
        Entry("家具", catThree = "通勤绝路"),

        Entry("活动", catThree = "桃源村志"),
        Entry("活动", catThree = "再探桃源"),
        Entry("活动", catThree = "三千宇宙"),
        Entry("活动", catThree = "袁氏祖宅"),
        Entry("活动", catThree = "仙殒"),
        Entry("活动", catThree = "广陵成长计划"),
        Entry("活动", catThree = "海岛漂流"),
        Entry("活动", catThree = "云梦巫乡"),
        Entry("活动", catThree = "三国志魂魂版"),
        Entry("活动", catThree = "魂魂迷城"),
        Entry("活动", catThree = "梦入浮生"),
        Entry("活动", catThree = "乘风破浪公务"),
        Entry("活动", catThree = "江东万里船"),
        Entry("活动", catThree = "月海夜航船"),
        Entry("活动", catThree = "七载相逢之秋"),
        Entry("活动", catThree = "桃源温泉山庄"),
        Entry("活动", catThree = "危急存亡之秋"),
        Entry("活动", catThree = "燃灯(犀)照夜"),
        Entry("活动", catThree = "剑剑剑来"),
        Entry("活动", catThree = "双魂成行"),
        Entry("活动", catThree = "斧·蝶·梦"),
        Entry("活动", catThree = "三国志绒绒版"),
        Entry("活动", catThree = "游神大乱斗"),
        Entry("活动", catThree = "绒绒世界悲"),
        Entry("活动", catThree = "东海海水浴场"),
        Entry("活动", catThree = "陶生"),
        Entry("活动", catThree = "华堂夜宴"),
        Entry("活动", catThree = "魂生一串"),
        Entry("活动", catThree = "邺城之战"),

        Entry("白鹄", catThree = "24年11月"),
        Entry("白鹄", catThree = "24年12月"),
        Entry("白鹄", catThree = "25年01月"),
        Entry("白鹄", catThree = "25年02月"),
        Entry("白鹄", catThree = "25年03月"),
        Entry("白鹄", catThree = "25年04月"),
        Entry("白鹄", catThree = "25年05月"),
        Entry("白鹄", catThree = "25年06月"),
        Entry("白鹄", catThree = "25年07月"),
        Entry("白鹄", catThree = "25年08月"),
        Entry("白鹄", catThree = "25年09月"),
        Entry("白鹄", catThree = "25年10月"),
        Entry("白鹄", catThree = "25年11月"),
        Entry("白鹄", catThree = "25年12月"),
        Entry("白鹄", catThree = "26年01月"),
        Entry("白鹄", catThree = "26年02月"),
        Entry("白鹄", catThree = "26年03月"),
        Entry("白鹄", catThree = "26年04月"),
        Entry("白鹄", catThree = "26年05月"),
        Entry("白鹄", catThree = "26年06月"),
        Entry("白鹄", catThree = "26年07月"),
        Entry("白鹄", catThree = "26年08月"),
        Entry("白鹄", catThree = "26年09月"),
        Entry("白鹄", catThree = "26年10月"),
        Entry("白鹄", catThree = "26年11月"),
        Entry("白鹄", catThree = "26年12月"),

        Entry("其他", catThree = "其他"),
    )

    val levels: List<ArkLevel> = entries.mapIndexed { index, entry ->
        val stageId = buildStageId(entry, index)
        ArkLevel(
            levelId = stageId,
            stageId = stageId,
            sha = "manual-$stageId",
            catOne = entry.catOne,
            catTwo = entry.catTwo ?: "",
            catThree = entry.catThree,
            name = entry.catThree,
            isOpen = true,
        )
    }

    private fun buildStageId(entry: Entry, index: Int): String {
        val segments = listOf(entry.catOne, entry.catTwo ?: "", entry.catThree)
            .map(::sanitize)
            .filter(String::isNotBlank)
        val joined = segments.joinToString("-")
        return if (joined.isNotBlank()) joined else "manual-${index + 1}"
    }

    private fun sanitize(value: String): String = value.filterNot(Char::isWhitespace)
}
