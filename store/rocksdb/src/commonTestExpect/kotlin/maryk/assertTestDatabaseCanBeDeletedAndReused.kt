package maryk

import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.test.models.TestMarykModel
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal suspend fun assertTestDatabaseCanBeDeletedAndReused() {
    val path = createTestDBFolder("delete-folder-reuse")

    try {
        RocksDBDataStore.open(
            relativePath = path,
            dataModelsById = mapOf(1u to TestMarykModel),
        ).close()

        assertTrue(deleteFolder(path))
        assertFalse(doesFolderExist(path))

        RocksDBDataStore.open(
            relativePath = path,
            dataModelsById = mapOf(1u to TestMarykModel),
        ).close()
    } finally {
        deleteFolder(path)
    }
}

internal suspend fun assertDirectorySymlinkIsUnlinkedWithoutDeletingTarget(
    createSymbolicLink: (target: String, link: String) -> Boolean,
) {
    val folder = createTestDBFolder("delete-folder-symlink")
    val target = createTestDBFolder("delete-folder-symlink-target")

    try {
        RocksDBDataStore.open(
            relativePath = target,
            dataModelsById = mapOf(1u to TestMarykModel),
        ).close()

        val link = "$folder/target"
        if (!createSymbolicLink("../${target.substringAfterLast('/')}", link)) return

        assertTrue(doesFolderExist(link))
        assertTrue(deleteFolder(folder))
        assertTrue(doesFolderExist(target))
    } finally {
        deleteFolder(folder)
        deleteFolder(target)
    }
}
