package com.example.yuanassist.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TestActivityTemplateDisplayNameMappingTest {

    @Test
    fun jiedao1_is_labeled_as_night_street_scene() {
        assertEquals("突发情况夜晚街道场景", readTemplateDisplayName("jiedao1"))
    }

    @Test
    fun jiedao2_is_labeled_as_day_street_scene() {
        assertEquals("突发情况白天街道场景", readTemplateDisplayName("jiedao2"))
    }

    private fun readTemplateDisplayName(templateKey: String): String {
        val sourcePath = resolveRepoPath(
            "app/src/main/java/com/example/yuanassist/ui/TestActivity.kt"
        )
        val source = String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8)
        val pattern = Regex("\"$templateKey\"\\s+to\\s+\"([^\"]+)\"")
        val match = pattern.find(source)
            ?: throw AssertionError("Could not find display mapping for $templateKey in $sourcePath")
        return match.groupValues[1]
    }

    private fun resolveRepoPath(relativePath: String): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(6) {
            val candidate = current.resolve(relativePath)
            if (Files.exists(candidate)) {
                return candidate
            }
            val parent = current.parent ?: return@repeat
            current = parent
        }
        throw AssertionError("Could not resolve $relativePath from ${System.getProperty("user.dir")}")
    }
}
