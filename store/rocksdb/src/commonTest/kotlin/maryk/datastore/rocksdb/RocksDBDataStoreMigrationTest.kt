package maryk.datastore.rocksdb

import maryk.core.models.migration.MigrationException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RocksDBDataStoreMigrationTest {
    private val basePath = "./build/test-database"

    class CustomException : Error()

    @Test
    fun testMigration() = runSuspendingTest {
        val path = "$basePath/migration"
        var dataStore = RocksDBDataStore(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV1,
            )
        )

        dataStore.close()

        dataStore = RocksDBDataStore(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV1_1,
            )
        )

        dataStore.close()

        assertFailsWith<MigrationException> {
            // Missing migration handler so will throw exception
            dataStore = RocksDBDataStore(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationHandler = null
            )
        }

        assertFailsWith<CustomException> {
            dataStore = RocksDBDataStore(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationHandler = { _, storedDataModel, newDataModel ->
                    assertEquals(ModelV2, newDataModel)
                    assertEquals(ModelV1_1.Model.version, storedDataModel.Model.version)
                    // Should throw this exception to proof it is entering this handler
                    throw CustomException()
                }
            )
        }

        Unit
    }

    @Test
    fun testMigrationWithIndex() = runSuspendingTest {
        val path = "$basePath/migration2"
        var dataStore = RocksDBDataStore(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV2,
            )
        )

        val addResult = dataStore.execute(
            ModelV2.add(
                ModelV2.run { create (value with "ha1", newNumber with 100) },
                ModelV2.run { create (value with "ha2", newNumber with 50) },
                ModelV2.run { create (value with "ha3", newNumber with 3500) },
                ModelV2.run { create (value with "ha4", newNumber with 1) },
            )
        )

        assertEquals(4, addResult.statuses.size)

        val keys = mutableListOf<Key<ModelV2>>()

        for (status in addResult.statuses) {
            assertIs<AddSuccess<ModelV2>>(status).apply {
                keys.add(key)
            }
        }

        val changeResult = dataStore.execute(
            ModelV2.change(
                keys[0].change(Change(ModelV2 { newNumber:: ref} with 40)),
                keys[1].change(Change(ModelV2 { newNumber:: ref} with 2000)),
                keys[2].change(Change(ModelV2 { newNumber:: ref} with 500)),
                keys[3].change(Change(ModelV2 { newNumber:: ref} with 990))
            )
        )

        for (status in changeResult.statuses) {
            assertIs<ChangeSuccess<ModelV2>>(status)
        }

        dataStore.close()

        dataStore = RocksDBDataStore(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV2ExtraIndex,
            )
        )

        val scanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.ascending()
            )
        )

        assertEquals(4, scanResponse.values.size)

        assertEquals(40, scanResponse.values[0].values { newNumber })
        assertEquals(500, scanResponse.values[1].values { newNumber })
        assertEquals(990, scanResponse.values[2].values { newNumber })
        assertEquals(2000, scanResponse.values[3].values { newNumber })

        val historicScanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                toVersion = ULong.MAX_VALUE
            )
        )

        assertEquals(4, historicScanResponse.values.size)

        assertEquals(2000, historicScanResponse.values[0].values { newNumber })
        assertEquals(990, historicScanResponse.values[1].values { newNumber })
        assertEquals(500, historicScanResponse.values[2].values { newNumber })
        assertEquals(40, historicScanResponse.values[3].values { newNumber })

        dataStore.close()
    }
}
