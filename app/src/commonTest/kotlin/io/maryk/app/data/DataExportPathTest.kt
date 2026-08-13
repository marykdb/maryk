package io.maryk.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DataExportPathTest {
    @Test
    fun joinsEmptyFolderWithoutRootingPath() {
        assertEquals("record.json", joinExportPath("", "record.json"))
    }

    @Test
    fun joinsFolderWithoutDuplicatingSeparator() {
        assertEquals("exports/record.json", joinExportPath("exports/", "record.json"))
        assertEquals("exports/record.json", joinExportPath("exports", "record.json"))
        assertEquals("exports/record.json", joinExportPath("exports\\", "record.json"))
    }

    @Test
    fun preservesRootExportFolder() {
        assertEquals("/record.json", joinExportPath("/", "record.json"))
        assertEquals("\\record.json", joinExportPath("\\", "record.json"))
        assertEquals("C:/record.json", joinExportPath("C:\\", "record.json"))
        assertEquals("C:/record.json", joinExportPath("C:/", "record.json"))
    }

    @Test
    fun sanitizesFilePartsForPortableExportNames() {
        assertEquals("data", sanitizeFilePart(""))
        assertEquals("data-f7d93e17ec4b1219", sanitizeFilePart("..."))
        assertEquals("_CON-ba0f119aa5d684b", sanitizeFilePart("CON"))
        assertEquals("_nul.data-b7085d27166b01ba", sanitizeFilePart("nul.data"))
        assertEquals("a_2f_b_5c_c-9eb9e1bc4306790c", sanitizeFilePart("a/b\\c"))
        assertEquals("valid.Name-1_2", sanitizeFilePart("valid.Name-1_2"))
    }

    @Test
    fun limitsFilePartLength() {
        assertEquals(120, sanitizeFilePart("a".repeat(200)).length)
    }

    @Test
    fun keepsDistinctUnsafeFilePartsDistinct() {
        assertNotEquals(sanitizeFilePart("records/2026"), sanitizeFilePart("records:2026"))
        assertNotEquals(sanitizeFilePart("a".repeat(200) + "/one"), sanitizeFilePart("a".repeat(200) + "/two"))
    }

    @Test
    fun givesCaseDistinctModelsPortableExportNames() {
        val names = modelExportFileNames(listOf("Person", "person"), ModelExportFormat.JSON)

        assertEquals(2, names.values.map { it.lowercase() }.toSet().size)
    }

    @Test
    fun givesCaseDistinctModelsPortableDataExportNames() {
        val names = dataExportFileNames(listOf("Person", "person"), DataExportFormat.JSON)

        assertEquals(2, names.values.map { it.lowercase() }.toSet().size)
    }
}
