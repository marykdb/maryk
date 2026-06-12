package io.maryk.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals("data", sanitizeFilePart("..."))
        assertEquals("_CON", sanitizeFilePart("CON"))
        assertEquals("_nul.data", sanitizeFilePart("nul.data"))
        assertEquals("a_b_c", sanitizeFilePart("a/b\\c"))
        assertEquals("valid.Name-1_2", sanitizeFilePart("valid.Name-1_2"))
    }

    @Test
    fun limitsFilePartLength() {
        assertEquals(120, sanitizeFilePart("a".repeat(200)).length)
    }
}
