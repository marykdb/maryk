package maryk.file

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileTest {
    @Test
    fun writeAndReadBack() {
        val path = "fileStoreTest-${Random.nextInt()}.txt"

        assertNull(File.readText(path))

        File.writeText(path, "hello")
        assertEquals("hello", File.readText(path))

        File.writeText(path, "bye")
        assertEquals("bye", File.readText(path))

        assertEquals(true, File.delete(path))
        assertNull(File.readText(path))
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
}
