package io.maryk.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class SaveContextTest {
    @Test
    fun sanitizesSaveFileNames() {
        assertEquals("data", sanitizeSaveFileName(""))
        assertEquals("data", sanitizeSaveFileName("..."))
        assertEquals("_CON", sanitizeSaveFileName("CON"))
        assertEquals("_nul.data", sanitizeSaveFileName("nul.data"))
        assertEquals("a_b_c", sanitizeSaveFileName("a/b\\c"))
        assertEquals("valid.Name-1_2", sanitizeSaveFileName("valid.Name-1_2"))
    }

    @Test
    fun limitsSaveFileNameLength() {
        assertEquals(120, sanitizeSaveFileName("a".repeat(200)).length)
    }

    @Test
    fun joinsEmptySaveDirectoryWithoutRootingPath() {
        assertEquals("record.yaml", joinSavePath("", "record.yaml"))
    }

    @Test
    fun joinsSaveDirectoryWithoutDuplicatingSeparator() {
        assertEquals("exports/record.yaml", joinSavePath("exports/", "record.yaml"))
        assertEquals("exports/record.yaml", joinSavePath("exports", "record.yaml"))
        assertEquals("exports/record.yaml", joinSavePath("exports\\", "record.yaml"))
    }

    @Test
    fun rejectsKotlinOutputNameCollisionAfterSanitizing() {
        val context = SaveContext(
            key = "record",
            dataYaml = "",
            dataJson = "",
            dataProto = ByteArray(0),
            metaYaml = "",
            metaJson = "",
            metaProto = ByteArray(0),
            kotlinGenerator = {
                KotlinSaveResult(
                    mapOf(
                        "a/b.kt" to "one",
                        "a\\b.kt" to "two",
                    )
                )
            },
        )

        assertEquals(
            "Kotlin save failed: duplicate output file name `a_b.kt` after sanitizing.",
            context.save("", SaveFormat.KOTLIN, includeMeta = false, packageName = "test"),
        )
    }
}
