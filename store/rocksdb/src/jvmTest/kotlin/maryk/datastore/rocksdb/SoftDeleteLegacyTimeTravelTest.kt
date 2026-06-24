package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.test.dataModelsForTests
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.lib.bytes.combineToByteArray
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SoftDeleteLegacyTimeTravelTest {
    @Test
    fun softDeleteFallbackUsesCurrentTableVersion() = runTest {
        val folder = createTestDBFolder("soft-delete-legacy-time-travel")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )

        val values = Log("legacy-log")
        val key = Log.key(values)

        val addResponse = dataStore.execute(
            Log.add(key to values)
        )
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(
            Log.delete(key, hardDelete = false)
        )
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val columnFamilies = dataStore.getColumnFamilies(Log) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val historicReference = combineToByteArray(key.bytes, SOFT_DELETE_INDICATOR, versionBytes).apply {
            invert(size - versionBytes.size)
        }
        dataStore.db.delete(columnFamilies.historic.table, historicReference)

        val getAtDelete = dataStore.execute(
            Log.get(
                key,
                toVersion = deleteStatus.version,
                filterSoftDeleted = false,
            )
        )
        assertEquals(1, getAtDelete.values.size)
        assertTrue(getAtDelete.values.first().isDeleted)

        val getBeforeDelete = dataStore.execute(
            Log.get(
                key,
                toVersion = deleteStatus.version - 1uL,
                filterSoftDeleted = false,
            )
        )
        assertEquals(1, getBeforeDelete.values.size)
        assertTrue(!getBeforeDelete.values.first().isDeleted)

        val scanAtDelete = dataStore.execute(
            Log.scan(
                toVersion = deleteStatus.version,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertTrue(scanAtDelete.values.any { it.key == key })
        assertTrue(scanAtDelete.values.first { it.key == key }.isDeleted)

        val scanBeforeDelete = dataStore.execute(
            Log.scan(
                toVersion = deleteStatus.version - 1uL,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertTrue(scanBeforeDelete.values.any { it.key == key })
        assertTrue(!scanBeforeDelete.values.first { it.key == key }.isDeleted)

        val changesResponse = dataStore.execute(
            Log.getChanges(
                key,
                maxVersions = 100u,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteChange(changesResponse.changes.firstOrNull()?.changes.orEmpty()))

        val singleVersionChangesResponse = dataStore.execute(
            Log.getChanges(
                key,
                maxVersions = 1u,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteChange(singleVersionChangesResponse.changes.firstOrNull()?.changes.orEmpty()))

        val scanChangesResponse = dataStore.execute(
            Log.scanChanges(
                startKey = key,
                includeStart = true,
                limit = 1u,
                maxVersions = 100u,
                filterSoftDeleted = false,
            )
        )
        val scanned = scanChangesResponse.changes.firstOrNull { it.key == key }
        assertTrue(hasSoftDeleteChange(scanned?.changes.orEmpty()))

        val singleVersionScanChangesResponse = dataStore.execute(
            Log.scanChanges(
                startKey = key,
                includeStart = true,
                limit = 1u,
                maxVersions = 1u,
                filterSoftDeleted = false,
            )
        )
        val singleVersionScanned = singleVersionScanChangesResponse.changes.firstOrNull { it.key == key }
        assertTrue(hasSoftDeleteChange(singleVersionScanned?.changes.orEmpty()))

        val getUpdatesResponse = dataStore.execute(
            Log.getUpdates(key, maxVersions = 100u, filterSoftDeleted = false)
        )
        assertTrue(hasSoftDeleteUpdate(getUpdatesResponse.updates))

        val scanUpdatesResponse = dataStore.execute(
            Log.scanUpdates(
                startKey = key,
                includeStart = true,
                limit = 1u,
                filterSoftDeleted = false
            )
        )
        assertTrue(hasSoftDeleteUpdate(scanUpdatesResponse.updates))

        dataStore.close()
        deleteFolder(folder)
    }

    @Test
    fun softDeleteFallbackAppearsInUpdateHistoryScanUpdates() = runTest {
        val folder = createTestDBFolder("soft-delete-legacy-time-travel-history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        )

        val values = Log("legacy-log-history")
        val key = Log.key(values)

        val addResponse = dataStore.execute(Log.add(key to values))
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(Log.delete(key, hardDelete = false))
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val columnFamilies = dataStore.getColumnFamilies(Log) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val historicReference = combineToByteArray(key.bytes, SOFT_DELETE_INDICATOR, versionBytes).apply {
            invert(size - versionBytes.size)
        }
        dataStore.db.delete(columnFamilies.historic.table, historicReference)

        val scanUpdatesResponse = dataStore.execute(
            Log.scanUpdates(
                startKey = key,
                includeStart = true,
                limit = 1u,
                filterSoftDeleted = false
            )
        )
        assertTrue(hasSoftDeleteUpdate(scanUpdatesResponse.updates))

        dataStore.close()
        deleteFolder(folder)
    }

    @Test
    fun softDeleteFallbackEmitsChangeWhenSelectedValuesAreEmpty() = runTest {
        val folder = createTestDBFolder("soft-delete-empty-select-fallback")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        )

        val values = Log("legacy-log-empty-select")
        val key = Log.key(values)

        val addResponse = dataStore.execute(Log.add(key to values))
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(Log.delete(key, hardDelete = false))
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val columnFamilies = dataStore.getColumnFamilies(Log) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val historicReference = combineToByteArray(key.bytes, SOFT_DELETE_INDICATOR, versionBytes).apply {
            invert(size - versionBytes.size)
        }
        dataStore.db.delete(columnFamilies.historic.table, historicReference)

        val emptySelect = Log.graph { emptyList() }

        val changesResponse = dataStore.execute(
            Log.getChanges(
                key,
                maxVersions = 1u,
                select = emptySelect,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteChange(changesResponse.changes.firstOrNull()?.changes.orEmpty()))

        val scanChangesResponse = dataStore.execute(
            Log.scanChanges(
                startKey = key,
                includeStart = true,
                limit = 1u,
                maxVersions = 1u,
                select = emptySelect,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteChange(scanChangesResponse.changes.firstOrNull { it.key == key }?.changes.orEmpty()))

        val getUpdatesResponse = dataStore.execute(
            Log.getUpdates(
                key,
                maxVersions = 1u,
                select = emptySelect,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteUpdate(getUpdatesResponse.updates))

        val scanUpdatesResponse = dataStore.execute(
            Log.scanUpdates(
                startKey = key,
                includeStart = true,
                limit = 1u,
                select = emptySelect,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteUpdate(scanUpdatesResponse.updates))

        val updateHistoryResponse = dataStore.execute(
            Log.scanUpdateHistory(
                limit = 1u,
                fromVersion = deleteStatus.version,
                select = emptySelect,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteUpdate(updateHistoryResponse.updates))

        dataStore.close()
        deleteFolder(folder)
    }
}

private fun hasSoftDeleteChange(changes: List<VersionedChanges>): Boolean =
    changes.any { versioned ->
        versioned.changes.any { it is ObjectSoftDeleteChange }
    }

private fun <DM : IsRootDataModel> hasSoftDeleteUpdate(
    updates: List<IsUpdateResponse<DM>>
): Boolean =
    updates.filterIsInstance<ChangeUpdate<DM>>().any { update ->
        update.changes.any { it is ObjectSoftDeleteChange }
    }
