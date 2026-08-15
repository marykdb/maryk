package maryk

import kotlinx.coroutines.test.runTest
import platform.posix.symlink
import kotlin.test.Test

class DeleteFolderTest {
    @Test
    fun deletesNonEmptyTestDatabaseAndAllowsImmediatePathReuse() = runTest {
        assertTestDatabaseCanBeDeletedAndReused()
    }

    @Test
    fun unlinksDirectorySymlinkWithoutDeletingTarget() = runTest {
        assertDirectorySymlinkIsUnlinkedWithoutDeletingTarget { target, link ->
            symlink(target, link) == 0
        }
    }
}
