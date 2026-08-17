package maryk.file

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileTest {
    @Test
    fun writeAndReadBack() {
        val path = "fileStoreTest-${Random.nextInt()}.txt"

        assertNull(File.readText(path))
        assertNull(File.size(path))

        File.writeText(path, "hello")
        assertEquals("hello", File.readText(path))
        assertEquals(5L, File.size(path))

        File.writeText(path, "bye")
        assertEquals("bye", File.readText(path))
        assertEquals(3L, File.size(path))

        assertEquals(true, File.delete(path))
        assertNull(File.readText(path))
        assertNull(File.size(path))
    }

    @Test
    fun appendText() {
        val path = "fileStoreAppend-${Random.nextInt()}.txt"

        File.writeText(path, "a")
        File.appendText(path, "b")
        File.appendText(path, "c")

        assertEquals("abc", File.readText(path))

        File.delete(path)
    }

    @Test
    fun writeCreatesParentDirectories() {
        val path = "build/fileStoreNested-${Random.nextInt()}/nested/value.txt"

        File.writeText(path, "hello")

        assertEquals("hello", File.readText(path))

        File.delete(path)
    }

    @Test
    fun temporaryFileWriteReplacesExistingContents() {
        val path = "fileStoreAtomic-${Random.nextInt()}.txt"

        try {
            File.writeText(path, "old")
            File.writeTextViaTemporaryFile(path, "new")

            assertEquals("new", File.readText(path))
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun temporaryFileSyncFailurePreservesExistingContentsAndCleansTemporaryFile() {
        val path = "fileStoreAtomicFailure-${Random.nextInt()}.txt"
        var temporaryPath: String? = null

        try {
            File.writeText(path, "old")

            assertFailsWith<IllegalStateException> {
                File.writeTextViaTemporaryFile(path, "new") { temporary ->
                    temporaryPath = temporary
                    false
                }
            }

            assertEquals("old", File.readText(path))
            assertNotNull(temporaryPath)
            assertNull(File.readText(temporaryPath))
        } finally {
            File.delete(path)
            temporaryPath?.let(File::delete)
        }
    }

    @Test
    fun moveReplaceReplacesExistingContents() {
        val sourcePath = "fileStoreMoveSource-${Random.nextInt()}.txt"
        val destinationPath = "fileStoreMoveDestination-${Random.nextInt()}.txt"

        try {
            File.writeText(sourcePath, "new")
            File.writeText(destinationPath, "old")

            File.moveReplace(sourcePath, destinationPath)

            assertNull(File.readText(sourcePath))
            assertEquals("new", File.readText(destinationPath))
        } finally {
            File.delete(sourcePath)
            File.delete(destinationPath)
        }
    }

    @Test
    fun directoryIsNotReadableFile() {
        assertNull(File.size("."))
        assertNull(File.readText("."))
        assertNull(File.readBytes("."))
    }

    @Test
    fun readsRegularFilesInBoundedChunks() {
        val path = "fileStoreChunks-${Random.nextInt()}.txt"
        try {
            File.writeText(path, "abcdef")
            val chunks = mutableListOf<String>()

            assertEquals(true, File.readChunks(path, 2) { chunks += it.decodeToString() })
            assertEquals(listOf("ab", "cd", "ef"), chunks)
            assertFalse(File.readChunks("missing-$path", 2) {})
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun boundedReadRejectsContentOverLimit() {
        val path = "fileStoreBounded-${Random.nextInt()}.txt"
        try {
            File.writeText(path, "abcdef")

            assertEquals("abcdef", File.readText(path, 6))
            assertNull(File.readText(path, 5))
            assertNull(File.readBytes(path, 5))
        } finally {
            File.delete(path)
        }
    }

    @Test
    fun windowsParentDirectoriesTreatUncShareAsRoot() {
        assertEquals(
            listOf("\\\\server\\share\\folder", "\\\\server\\share\\folder\\nested"),
            windowsParentDirectories("\\\\server\\share\\folder\\nested\\value.txt"),
        )
        assertEquals(
            listOf("C:\\folder", "C:\\folder\\nested"),
            windowsParentDirectories("C:\\folder\\nested\\value.txt"),
        )
    }
}
