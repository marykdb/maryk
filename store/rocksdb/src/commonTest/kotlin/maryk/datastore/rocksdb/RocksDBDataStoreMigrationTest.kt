package maryk.datastore.rocksdb

import maryk.datastore.shared.migration.MigrationException
import maryk.rocksdb.DBOptions
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RocksDBDataStoreMigrationTest {
    private val rocksDBOptions = DBOptions().apply {
        setCreateIfMissing(true)
        setCreateMissingColumnFamilies(true)
    }

    private val basePath = "./build/test-database"

    class CustomException : Throwable()

    @Test
    fun testMigration() = runSuspendingTest {
        val path = "$basePath/migration"
        var dataStore = RocksDBDataStore(
            keepAllVersions = false,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV1
            ),
            rocksDBOptions = rocksDBOptions
        )

        dataStore.close()

        dataStore = RocksDBDataStore(
            keepAllVersions = false,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV1_1
            ),
            rocksDBOptions = rocksDBOptions
        )

        dataStore.close()

        assertFailsWith<MigrationException> {
            // Missing migration handler so will throw exception
            dataStore = RocksDBDataStore(
                keepAllVersions = false,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to ModelV2
                ),
                rocksDBOptions = rocksDBOptions,
                migrationHandler = null
            )
        }

        assertFailsWith<CustomException> {
            dataStore = RocksDBDataStore(
                keepAllVersions = false,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to ModelV2
                ),
                rocksDBOptions = rocksDBOptions,
                migrationHandler = { _, storedDataModel, newDataModel ->
                    assertEquals(ModelV2, newDataModel)
                    assertEquals(ModelV1_1.version, storedDataModel.version)
                    // Should throw this exception to proof it is entering this handler
                    throw CustomException()
                }
            )
        }

        Unit
    }
}
