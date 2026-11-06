package maryk

import kotlinx.coroutines.test.runTest
import maryk.file.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteFolderTest {
    @Test
    fun deletesNonEmptyTestDatabaseAndAllowsImmediatePathReuse() = runTest {
        assertTestDatabaseCanBeDeletedAndReused()
    }

    @Test
    fun unicodeDirectoriesAndEntriesCanBeCreatedDeletedAndReused() {
        val path = createTestDBFolder("folder-İ-🧪")
        try {
            assertTrue(doesFolderExist(path))
            val nested = "$path/nested-İ-🧪"
            assertTrue(createFolder(nested))
            File.writeText("$nested/content-İ-🧪.txt", "test")
            assertTrue(deleteFolder(path))
            assertFalse(doesFolderExist(path))
            assertTrue(createFolder(path))
            assertTrue(doesFolderExist(path))
        } finally {
            assertTrue(deleteFolder(path))
        }
    }
}
