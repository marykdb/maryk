package maryk.file

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FileDurabilityTest {
    @Test
    fun syncsTemporaryFileAndItsParentDirectory() {
        val directory = Files.createTempDirectory("maryk-file-sync-")
        val path = directory.resolve("metadata.yml").toString()

        try {
            File.writeText(path, "metadata")

            assertTrue(File.syncFile(path))
            assertTrue(File.syncParentDirectory(path))
        } finally {
            Files.deleteIfExists(directory.resolve("metadata.yml"))
            Files.deleteIfExists(directory)
        }
    }
}
