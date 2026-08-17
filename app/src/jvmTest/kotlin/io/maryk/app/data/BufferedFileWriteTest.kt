package io.maryk.app.data

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class BufferedFileWriteTest {
    @Test
    fun writesAllChunksThroughOneBufferedSession() = runTest {
        val directory = Files.createTempDirectory("maryk-buffered-export-")
        val path = directory.resolve("nested/export.bin")

        writeBufferedFile(path.toString()) { write ->
            write(byteArrayOf(1, 2))
            write(byteArrayOf(3))
            write(byteArrayOf(4, 5))
        }

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), Files.readAllBytes(path))
    }
}
