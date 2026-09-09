package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.yuanassist.core.XiuweiCatalog
import com.example.yuanassist.core.XiuweiJob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedXiuweiAgent(
    val id: Int,
    val job: XiuweiJob,
    val count: String,
    val now: String,
    val target: String,
)

data class SavedXiuweiState(
    val inventory: Map<String, Int>,
    val agents: List<SavedXiuweiAgent>,
    val previewBitmap: Bitmap?,
)

object XiuweiCalculatorStore {
    private const val PREFS_NAME = "xiuwei_calculator_prefs"
    private const val KEY_STATE = "state"
    private const val PREVIEW_DIR = "xiuwei_calculator"
    private const val PREVIEW_NAME = "last_preview.jpg"

    fun load(context: Context): SavedXiuweiState {
        val defaults = XiuweiCatalog.materials.associate { it.id to 0 }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = runCatching { JSONObject(prefs.getString(KEY_STATE, "{}").orEmpty()) }.getOrElse { JSONObject() }
        val inventoryObject = state.optJSONObject("inventory")
        val inventory = defaults.mapValues { (id, default) -> inventoryObject?.optInt(id, default) ?: default }
        val savedAgents = state.optJSONArray("agents")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val job = runCatching { XiuweiJob.valueOf(item.optString("job")) }.getOrNull() ?: continue
                    add(
                        SavedXiuweiAgent(
                            id = item.optInt("id", index + 1),
                            job = job,
                            count = item.optString("count", "1"),
                            now = item.optString("now", "1"),
                            target = item.optString("target", "17"),
                        ),
                    )
                }
            }
        }.orEmpty()
        return SavedXiuweiState(
            inventory = inventory,
            agents = savedAgents,
            previewBitmap = previewFile(context).takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) },
        )
    }

    fun saveInputs(
        context: Context,
        inventory: Map<String, Int>,
        agents: List<SavedXiuweiAgent>,
    ) {
        val inventoryObject = JSONObject()
        inventory.forEach { (id, count) -> inventoryObject.put(id, count.coerceAtLeast(0)) }
        val agentsArray = JSONArray()
        agents.forEach { agent ->
            agentsArray.put(
                JSONObject()
                    .put("id", agent.id)
                    .put("job", agent.job.name)
                    .put("count", agent.count)
                    .put("now", agent.now)
                    .put("target", agent.target),
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, JSONObject().put("inventory", inventoryObject).put("agents", agentsArray).toString())
            .apply()
    }

    fun savePreview(context: Context, bitmap: Bitmap) {
        runCatching {
            val file = previewFile(context)
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output) }
        }.onFailure { RunLogger.e(module = "修为计算", section = "数据保存", message = "保存上次背包预览失败", throwable = it) }
    }

    private fun previewFile(context: Context): File = File(context.filesDir, "$PREVIEW_DIR/$PREVIEW_NAME")
}
