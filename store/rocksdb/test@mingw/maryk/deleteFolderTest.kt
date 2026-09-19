package maryk

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteFolderTest {
    @Test
    fun deletesNonEmptyTestDatabaseAndAllowsImmediatePathReuse() = runTest {
        assertTestDatabaseCanBeDeletedAndReused()
    }
}
