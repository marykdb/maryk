package maryk.datastore.rocksdb.metadata

import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.file.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetaFileTest {
    private lateinit var path: String

    @BeforeTest
    fun setUp() {
        path = createTestDBFolder("meta-file")
    }

    @AfterTest
    fun tearDown() {
        deleteFolder(path)
    }

    @Test
    fun writesAndReadsMetaFileRoundTrip() {
        val metas = linkedMapOf<UInt, ModelMeta>(
            1u to ModelMeta("ModelName", 16),
            2u to ModelMeta("Another Model", 8),
        )

        writeStoreMetaFile(path, StoreMeta(metas, CURRENT_INDEX_KEY_FORMAT_VERSION))

        // Verify exact YAML structure
        val expected = """
            |version: 1
            |indexKeyFormatVersion: 2
            |models:
            |  1:
            |    name: ModelName
            |    keySize: 16
            |  2:
            |    name: Another Model
            |    keySize: 8
            |
        """.trimMargin()

        val stored = File.readText("$path/MARYK_META.yml")
        assertEquals(expected, stored)

        val readBack = readMetaFile(path)
        assertEquals(metas, readBack)
        assertEquals(CURRENT_INDEX_KEY_FORMAT_VERSION, readStoreMetaFile(path).indexKeyFormatVersion)
    }

    @Test
    fun readsModelNamesAndKeySizes() {
        val metas = mapOf<UInt, ModelMeta>(
            5u to ModelMeta("Fancy", 12),
            9u to ModelMeta("Other", 24),
        )
        writeMetaFile(path, metas)

        val readBack = readMetaFile(path)
        assertEquals(metas, readBack)

        val names = readModelNames(path)
        val keySizes = readModelKeySizes(path)

        assertEquals(mapOf(5u to "Fancy", 9u to "Other"), names)
        assertEquals(mapOf(5u to 12, 9u to 24), keySizes)
    }

    @Test
    fun returnsEmptyMapWhenFileMissing() {
        val readBack = readMetaFile(path)
        assertTrue(readBack.isEmpty())
        assertTrue(readModelNames(path).isEmpty())
        assertTrue(readModelKeySizes(path).isEmpty())
    }

    @Test
    fun rejectsInvalidKeySizes() {
        File.writeText(
            "$path/MARYK_META.yml",
            """
                |version: 1
                |indexKeyFormatVersion: 2
                |models:
                |  1:
                |    name: Broken
                |    keySize: 0
                |
            """.trimMargin()
        )

        assertFailsWith<IllegalArgumentException> {
            readMetaFile(path)
        }

        assertFailsWith<IllegalArgumentException> {
            ModelMeta("Broken", -1)
        }
    }

    @Test
    fun rejectsOverflowingKeySizes() {
        File.writeText(
            "$path/MARYK_META.yml",
            """
                |version: 1
                |indexKeyFormatVersion: 2
                |models:
                |  1:
                |    name: Broken
                |    keySize: 4294967297
                |
            """.trimMargin()
        )

        assertFailsWith<IllegalArgumentException> {
            readMetaFile(path)
        }
    }

    @Test
    fun rejectsNonIntegerKeySizes() {
        File.writeText(
            "$path/MARYK_META.yml",
            """
                |version: 1
                |indexKeyFormatVersion: 2
                |models:
                |  1:
                |    name: Broken
                |    keySize: 1.5
                |
            """.trimMargin()
        )

        assertFailsWith<IllegalArgumentException> {
            readMetaFile(path)
        }
    }

    @Test
    fun defaultsMissingIndexKeyFormatVersionToLegacy() {
        File.writeText(
            "$path/MARYK_META.yml",
            """
                |version: 1
                |models:
                |  1:
                |    name: Legacy
                |    keySize: 8
                |
            """.trimMargin()
        )

        val storeMeta = readStoreMetaFile(path)
        assertEquals(LEGACY_INDEX_KEY_FORMAT_VERSION, storeMeta.indexKeyFormatVersion)
        assertEquals(mapOf(1u to ModelMeta("Legacy", 8)), storeMeta.models)
    }
}
