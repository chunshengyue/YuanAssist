package com.example.yuanassist.core

import kotlin.math.ceil

enum class XiuweiJob(val key: String, val label: String) {
    FENGHUO("fenghuo", "风火"),
    DISHUI("dishui", "地水"),
    YINYANG("yinyang", "阴阳"),
}

data class XiuweiMaterial(
    val id: String,
    val name: String,
    val job: XiuweiJob,
    val assetName: String,
)

data class XiuweiAgentInput(
    val job: XiuweiJob,
    val count: Int,
    val now: Int,
    val target: Int,
)

data class XiuweiStagePlan(
    val stage: Int,
    val times: Int,
)

data class XiuweiCalculationResult(
    val requiredByJob: Map<XiuweiJob, IntArray>,
    val missingByJob: Map<XiuweiJob, IntArray>,
    val stagePlans: Map<XiuweiJob, List<XiuweiStagePlan>>,
    val totalRuns: Int,
    val totalStamina: Int,
)

object XiuweiCatalog {
    val materials: List<XiuweiMaterial> = listOf(
        XiuweiMaterial("fh_1", "绢扇", XiuweiJob.FENGHUO, "绢扇.png"),
        XiuweiMaterial("fh_2", "翠扇", XiuweiJob.FENGHUO, "翠扇.png"),
        XiuweiMaterial("fh_3", "金丝扇", XiuweiJob.FENGHUO, "金丝扇.png"),
        XiuweiMaterial("fh_4", "羽扇", XiuweiJob.FENGHUO, "羽扇.png"),
        XiuweiMaterial("fh_5", "仙门扇", XiuweiJob.FENGHUO, "仙门扇.png"),
        XiuweiMaterial("fh_6", "悲回风扇", XiuweiJob.FENGHUO, "悲回风扇.png"),
        XiuweiMaterial("ds_1", "浊酒", XiuweiJob.DISHUI, "浊酒.png"),
        XiuweiMaterial("ds_2", "清酒", XiuweiJob.DISHUI, "清酒.png"),
        XiuweiMaterial("ds_3", "百末旨酒", XiuweiJob.DISHUI, "百末旨酒.png"),
        XiuweiMaterial("ds_4", "灵山泉", XiuweiJob.DISHUI, "灵山泉.png"),
        XiuweiMaterial("ds_5", "霸王泪", XiuweiJob.DISHUI, "霸王泪.png"),
        XiuweiMaterial("ds_6", "木兰坠露", XiuweiJob.DISHUI, "木兰坠露.png"),
        XiuweiMaterial("yy_1", "铜镜", XiuweiJob.YINYANG, "铜镜.png"),
        XiuweiMaterial("yy_2", "六博镜", XiuweiJob.YINYANG, "六博镜.png"),
        XiuweiMaterial("yy_3", "鎏金镜", XiuweiJob.YINYANG, "鎏金镜.png"),
        XiuweiMaterial("yy_4", "宝石镜", XiuweiJob.YINYANG, "宝石镜.png"),
        XiuweiMaterial("yy_5", "水镜", XiuweiJob.YINYANG, "水镜.png"),
        XiuweiMaterial("yy_6", "星汉镜", XiuweiJob.YINYANG, "星汉镜.png"),
    )

    val jobs: List<XiuweiJob> = XiuweiJob.entries

    // cl[修为] from the wiki calculator. Each row is the six materials in one job.
    private val levelCosts: Map<Int, IntArray> = mapOf(
        2 to intArrayOf(20, 0, 0, 0, 0, 0),
        3 to intArrayOf(40, 0, 0, 0, 0, 0),
        4 to intArrayOf(60, 50, 0, 0, 0, 0),
        5 to intArrayOf(60, 80, 0, 0, 0, 0),
        6 to intArrayOf(0, 80, 120, 0, 0, 0),
        7 to intArrayOf(0, 100, 150, 0, 0, 0),
        8 to intArrayOf(0, 120, 180, 0, 0, 0),
        9 to intArrayOf(0, 140, 210, 0, 0, 0),
        10 to intArrayOf(0, 0, 240, 360, 0, 0),
        11 to intArrayOf(0, 0, 260, 390, 0, 0),
        12 to intArrayOf(0, 0, 280, 420, 0, 0),
        13 to intArrayOf(0, 0, 0, 440, 660, 0),
        14 to intArrayOf(0, 0, 0, 460, 690, 0),
        15 to intArrayOf(0, 0, 0, 480, 720, 0),
        16 to intArrayOf(0, 0, 0, 0, 600, 900),
        17 to intArrayOf(0, 0, 0, 0, 750, 1200),
    )

    // The 12 stages and their six material rewards, copied from the calculator source.
    val stageRewards: List<IntArray> = listOf(
        intArrayOf(15, 0, 0, 0, 0, 0),
        intArrayOf(25, 0, 0, 0, 0, 0),
        intArrayOf(25, 15, 0, 0, 0, 0),
        intArrayOf(30, 20, 0, 0, 0, 0),
        intArrayOf(0, 35, 25, 0, 0, 0),
        intArrayOf(0, 40, 30, 0, 0, 0),
        intArrayOf(0, 0, 40, 30, 0, 0),
        intArrayOf(0, 0, 45, 35, 0, 0),
        intArrayOf(0, 0, 0, 45, 35, 0),
        intArrayOf(0, 0, 0, 50, 40, 0),
        intArrayOf(0, 0, 0, 0, 55, 40),
        intArrayOf(0, 0, 0, 0, 60, 45),
    )

    fun requiredForAgent(now: Int, target: Int): IntArray {
        require(now in 1..17 && target in 1..17 && target > now) {
            "当前修为和目标修为必须是 1-17，且目标修为更高"
        }
        return IntArray(6) { materialIndex ->
            (now + 1..target).sumOf { levelCosts[it]?.get(materialIndex) ?: 0 }
        }
    }
}

object XiuweiCalculator {
    fun calculate(
        agents: List<XiuweiAgentInput>,
        inventory: Map<String, Int>,
        maxStage: Int,
    ): XiuweiCalculationResult {
        require(agents.isNotEmpty()) { "请至少添加一个密探" }
        require(maxStage in 1..XiuweiCatalog.stageRewards.size) { "历练关卡范围无效" }

        val required = XiuweiJob.entries.associateWith { IntArray(6) }.toMutableMap()
        agents.forEach { agent ->
            require(agent.count > 0) { "密探数量必须大于 0" }
            val cost = XiuweiCatalog.requiredForAgent(agent.now, agent.target)
            val sum = required.getValue(agent.job)
            cost.forEachIndexed { index, value -> sum[index] += value * agent.count }
        }

        val missing = XiuweiJob.entries.associateWith { job ->
            val jobMaterials = XiuweiCatalog.materials.filter { it.job == job }
            IntArray(6) { index ->
                (required.getValue(job)[index] - (inventory[jobMaterials[index].id] ?: 0)).coerceAtLeast(0)
            }
        }

        val plans = mutableMapOf<XiuweiJob, List<XiuweiStagePlan>>()
        var totalRuns = 0
        XiuweiJob.entries.forEach { job ->
            val plan = XiuweiOptimizer.optimize(missing.getValue(job), maxStage)
            plans[job] = plan
            totalRuns += plan.sumOf { it.times }
        }

        return XiuweiCalculationResult(
            requiredByJob = required,
            missingByJob = missing,
            stagePlans = plans,
            totalRuns = totalRuns,
            totalStamina = totalRuns * 20,
        )
    }
}

private object XiuweiOptimizer {
    private data class Group(val stage: Int?, val first: Int, val second: Int)

    fun optimize(demand: IntArray, maxStage: Int): List<XiuweiStagePlan> {
        if (demand.all { it <= 0 }) return emptyList()
        val groups = buildGroups(maxStage)
        val upperCounts = feasibleUpperBound(demand, groups)
            ?: throw IllegalArgumentException("当前开启的历练关卡无法覆盖全部材料需求")
        val upperRuns = upperCounts.sum()

        var previous = IntArray(upperRuns + 1) { Int.MAX_VALUE }
        for (count in 0..upperRuns) previous[count] = count
        val parents = mutableListOf<IntArray>()

        for (groupIndex in 1..5) {
            val group = groups[groupIndex]
            val previousGroup = groups[groupIndex - 1]
            val demandIndex = groupIndex - 1
            val previousContribution = if (demandIndex == 0) previousGroup.first else previousGroup.second
            val suffixMin = IntArray(upperRuns + 2) { Int.MAX_VALUE }
            for (index in upperRuns downTo 0) {
                suffixMin[index] = minOf(previous[index], suffixMin[index + 1])
            }
            val current = IntArray(upperRuns + 1) { Int.MAX_VALUE }
            val parent = IntArray(upperRuns + 1) { -1 }
            for (nextCount in 0..upperRuns) {
                val threshold = if (previousContribution == 0) {
                    if (group.first * nextCount >= demand[demandIndex]) 0 else upperRuns + 1
                } else {
                    ceilPositive(demand[demandIndex] - group.first * nextCount, previousContribution)
                }
                if (threshold > upperRuns) continue
                val previousCost = suffixMin[threshold]
                if (previousCost == Int.MAX_VALUE) continue
                current[nextCount] = previousCost + nextCount
                parent[nextCount] = findFirstMin(previous, threshold)
            }
            previous = current
            parents += parent
        }

        var bestCount = -1
        var bestCost = Int.MAX_VALUE
        val finalGroup = groups[5]
        for (count in 0..upperRuns) {
            if (finalGroup.second * count < demand[5]) continue
            if (previous[count] < bestCost) {
                bestCost = previous[count]
                bestCount = count
            }
        }
        if (bestCount < 0) throw IllegalArgumentException("当前开启的历练关卡无法覆盖全部材料需求")

        val counts = IntArray(6)
        counts[5] = bestCount
        for (index in 4 downTo 1) {
            counts[index] = parents[index - 1][counts[index + 1]]
        }
        counts[0] = parents.firstOrNull()?.get(counts[1]) ?: bestCount

        return buildList {
            counts.forEachIndexed { index, count ->
                val stage = groups[index].stage
                if (stage != null && count > 0) add(XiuweiStagePlan(stage, count))
            }
        }
    }

    private fun buildGroups(maxStage: Int): List<Group> {
        return (0..5).map { index ->
            val firstStage = index * 2 + 1
            val lastStage = firstStage + 1
            val stage = when {
                maxStage < firstStage -> null
                maxStage >= lastStage -> lastStage
                else -> firstStage
            }
            val reward = stage?.let { XiuweiCatalog.stageRewards[it - 1] }
            when (index) {
                0 -> Group(stage, reward?.get(0) ?: 0, 0)
                else -> Group(stage, reward?.get(index - 1) ?: 0, reward?.get(index) ?: 0)
            }
        }
    }

    private fun feasibleUpperBound(demand: IntArray, groups: List<Group>): IntArray? {
        val counts = IntArray(6)
        for (index in 5 downTo 1) {
            val available = when (index) {
                5 -> groups[5].second
                else -> groups[index].second
            }
            val alreadyCovered = when (index) {
                5 -> 0
                else -> groups[index + 1].first * counts[index + 1]
            }
            val remaining = (demand[index] - alreadyCovered).coerceAtLeast(0)
            if (remaining == 0) continue
            if (available <= 0) return null
            counts[index] = ceilPositive(remaining, available)
        }
        val tier0Remaining = (demand[0] - groups[1].first * counts[1]).coerceAtLeast(0)
        if (tier0Remaining > 0) {
            if (groups[0].first <= 0) return null
            counts[0] = ceilPositive(tier0Remaining, groups[0].first)
        }
        return counts
    }

    private fun ceilPositive(value: Int, divisor: Int): Int =
        if (value <= 0) 0 else ceil(value.toDouble() / divisor.toDouble()).toInt()

    private fun findFirstMin(values: IntArray, from: Int): Int {
        var best = from
        for (index in from + 1 until values.size) {
            if (values[index] < values[best]) best = index
        }
        return best
    }
}
