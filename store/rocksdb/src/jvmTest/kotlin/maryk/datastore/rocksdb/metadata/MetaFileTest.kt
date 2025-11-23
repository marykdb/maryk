package maryk.datastore.rocksdb.metadata

import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        writeMetaFile(path, metas)

        // Verify exact YAML structure
        val expected = """
            |version: 1
            |models:
            |  1:
            |    name: ModelName
            |    keySize: 16
            |  2:
            |    name: Another Model
            |    keySize: 8
            |
        """.trimMargin()

        val stored = maryk.file.File.readText("$path/MARYK_META.yml")
        assertEquals(expected, stored)

        val readBack = readMetaFile(path)
        assertEquals(metas, readBack)
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
}
