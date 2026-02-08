@file:OptIn(ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
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
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.ModelWithDependents
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FoundationDBDataStoreMigrationTest {

    class CustomException : Error()

    @Test
    fun testComplexMigrationCheckWithNoChange() = runTest(timeout = 5.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-nochange", Uuid.random().toString())

        val firstStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = dataModelsForTests,
            databaseOptionsSetter = {
                setTransactionRetryLimit(3)
                setTransactionMaxRetryDelay(5000)
            }
        )
        try {
            // no-op, first open persists definitions
        } finally {
            firstStore.close()
        }

        val secondStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = dataModelsForTests,
            databaseOptionsSetter = {
                setTransactionRetryLimit(3)
                setTransactionMaxRetryDelay(5000)
            }
        )
        try {
            // no-op, second open validates no migration needed
        } finally {
            secondStore.close()
        }
    }

    @Test
    fun testMigration() = runTest(timeout = 5.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration", Uuid.random().toString())
        var didRunUpdateHandler = false

        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV1,
            ),
            versionUpdateHandler = { _, oldModel, newModel ->
                didRunUpdateHandler = true
                assertNull(oldModel)
                assertEquals(ModelV1, newModel)
            }
        )

        assertTrue { didRunUpdateHandler }

        dataStore.close()

        didRunUpdateHandler = false

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV1_1,
            ),
            versionUpdateHandler = { _, oldModel, newModel ->
                didRunUpdateHandler = true
                assertNotNull(oldModel)
                assertEquals(ModelV1_1, newModel)
            }
        )

        assertTrue { didRunUpdateHandler }

        dataStore.close()

        assertFailsWith<MigrationException> {
            // Missing migration handler so will throw exception
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationHandler = null
            )
        }

        assertFailsWith<CustomException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationHandler = { _, storedDataModel, newDataModel ->
                    assertEquals(ModelV2, newDataModel)
                    assertEquals(ModelV1_1.Meta.version, storedDataModel.Meta.version)
                    // Should throw this exception to proof it is entering this handler
                    throw CustomException()
                }
            )
        }
    }

    @Test
    fun testMigrationWithDependents() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-with-deps", Uuid.random().toString())
        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()
    }

    @Test
    fun testMigrationWithIndex() = runTest(timeout = 5.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-index", Uuid.random().toString())
        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV2,
            )
        )

        val addResult = dataStore.execute(
            ModelV2.add(
                ModelV2.create {
                    value with "ha1"
                    newNumber with 100
                },
                ModelV2.create {
                    value with "ha2"
                    newNumber with 50
                },
                ModelV2.create {
                    value with "ha3"
                    newNumber with 3500
                },
                ModelV2.create {
                    value with "ha4"
                    newNumber with 1
                },
            )
        )

        assertEquals(4, addResult.statuses.size)

        val keys = mutableListOf<Key<ModelV2>>()
        var initialMaxVersion = 0uL

        for (status in addResult.statuses) {
            assertIs<AddSuccess<ModelV2>>(status).apply {
                keys.add(key)
                if (version > initialMaxVersion) initialMaxVersion = version
            }
        }

        val changeResult = dataStore.execute(
            ModelV2.change(
                keys[0].change(Change(ModelV2 { newNumber::ref } with 40)),
                keys[1].change(Change(ModelV2 { newNumber::ref } with 2000)),
                keys[2].change(Change(ModelV2 { newNumber::ref } with 500)),
                keys[3].change(Change(ModelV2 { newNumber::ref } with 990))
            )
        )

        for (status in changeResult.statuses) {
            assertIs<ChangeSuccess<ModelV2>>(status)
        }

        dataStore.close()

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
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

        // Historic scan at time before changes: expect initial values ordering
        val preChangeHistoric = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                toVersion = initialMaxVersion
            )
        )

        assertEquals(4, preChangeHistoric.values.size)
        assertEquals(3500, preChangeHistoric.values[0].values { newNumber })
        assertEquals(100, preChangeHistoric.values[1].values { newNumber })
        assertEquals(50, preChangeHistoric.values[2].values { newNumber })
        assertEquals(1, preChangeHistoric.values[3].values { newNumber })

        dataStore.close()
    }

    @Test
    fun failsWhenModelIdIsReusedForDifferentModelName() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-name-mismatch", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        ).close()

        assertFailsWith<StorageException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to SimpleMarykModel)
            )
        }
    }
}
