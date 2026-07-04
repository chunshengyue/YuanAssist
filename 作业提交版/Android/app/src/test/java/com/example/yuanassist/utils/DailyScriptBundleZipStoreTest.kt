package com.example.yuanassist.utils

import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.TaskParams
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DailyScriptBundleZipStoreTest {
    @Test
    fun `packBundle writes script and templates then counts tasks`() {
        val root = createTempDir(prefix = "daily_zip_pack")
        try {
            val bundleRoot = File(root, "source").apply { mkdirs() }
            val templates = File(bundleRoot, "templates").apply { mkdirs() }
            val bundle = UserDailyScriptBundle("source", bundleRoot, File(bundleRoot, "script.json"), templates)
            val plan = DailyTaskPlan(
                start_task_id = 1,
                tasks = listOf(
                    DailyTask(id = 1, action = "CLICK", params = TaskParams(x = 1f, y = 2f)),
                    DailyTask(id = 2, action = "BACK", params = null),
                ),
            )
            UserDailyScriptStore.savePlan(bundle, plan, Gson())
            File(templates, "button.png").writeText("image-bytes", Charsets.UTF_8)

            val zip = DailyScriptBundleZipStore.packBundle(root, bundle, Gson())

            assertTrue(zip.file.exists())
            assertEquals(2, zip.taskCount)
            assertTrue(zip.sizeBytes > 0L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unpackToBundle restores script and templates`() {
        val root = createTempDir(prefix = "daily_zip_unpack")
        try {
            val sourceRoot = File(root, "source").apply { mkdirs() }
            val sourceTemplates = File(sourceRoot, "templates").apply { mkdirs() }
            val source = UserDailyScriptBundle("source", sourceRoot, File(sourceRoot, "script.json"), sourceTemplates)
            UserDailyScriptStore.savePlan(
                source,
                DailyTaskPlan(start_task_id = 1, tasks = listOf(DailyTask(id = 1, action = "BACK", params = null))),
                Gson(),
            )
            File(sourceTemplates, "a.png").writeText("template", Charsets.UTF_8)
            val zip = DailyScriptBundleZipStore.packBundle(root, source, Gson()).file
            val targetRoot = File(root, "target").apply { mkdirs() }
            val target = UserDailyScriptBundle("target", targetRoot, File(targetRoot, "script.json"), File(targetRoot, "templates"))

            val plan = DailyScriptBundleZipStore.unpackToBundle(zip, target, Gson())

            assertTrue(target.scriptFile.exists())
            assertEquals(1, plan.start_task_id)
            assertEquals("template", File(target.templatesDir, "a.png").readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unpackToBundle rejects zip slip entries`() {
        val root = createTempDir(prefix = "daily_zip_slip")
        try {
            val zip = File(root, "bad.zip")
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("../escape.txt"))
                output.write("bad".toByteArray())
                output.closeEntry()
            }
            val targetRoot = File(root, "target").apply { mkdirs() }
            val target = UserDailyScriptBundle("target", targetRoot, File(targetRoot, "script.json"), File(targetRoot, "templates"))

            try {
                DailyScriptBundleZipStore.unpackToBundle(zip, target, Gson())
                fail("Expected zip slip rejection")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("非法压缩包路径"))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
