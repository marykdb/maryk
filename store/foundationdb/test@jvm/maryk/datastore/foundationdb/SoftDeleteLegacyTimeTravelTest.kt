package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SoftDeleteLegacyTimeTravelTest {
    @Test
    fun currentOnlyMarkerMergesWithOlderHistoryWithinVersionLimit() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "mixed-legacy-soft-delete", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )
        try {
            val added = assertIs<AddSuccess<Log>>(store.execute(Log.add(Log("mixed-legacy"))).statuses.single())
            store.execute(Log.delete(added.key))
            store.execute(Log.change(added.key.change(ObjectSoftDeleteChange(false))))
            val deleted = assertIs<DeleteSuccess<Log>>(store.execute(Log.delete(added.key)).statuses.single())
            val dirs = store.getTableDirs(Log) as HistoricTableDirectories
            val historicKey = packVersionedKey(
                dirs.historicTablePrefix, added.key.bytes,
                encodeZeroFreeUsing01(byteArrayOf(SOFT_DELETE_INDICATOR)),
                version = HLC.toStorageBytes(HLC(deleted.version)),
            )
            store.runTransaction { it.clear(historicKey) }
            for ((limit, expected) in listOf(100u to listOf(true, false, true), 1u to listOf(true))) {
                val response = store.execute(Log.getChanges(
                    added.key, toVersion = deleted.version, maxVersions = limit, filterSoftDeleted = false,
                ))
                val states = response.changes.single().changes.flatMap { versioned ->
                    versioned.changes.filterIsInstance<ObjectSoftDeleteChange>().map { it.isDeleted }
                }
                assertEquals(expected, states)
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun softDeleteFallbackAppearsInChangesAndUpdates() = runTest {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "legacy-soft-delete", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        val values = Log("legacy-log-fdb")
        val key = Log.key(values)

        val addResponse = dataStore.execute(
            Log.add(key to values)
        )
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(
            Log.delete(key, hardDelete = false)
        )
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val tableDirs = dataStore.getTableDirs(Log) as HistoricTableDirectories
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val encodedQualifier = encodeZeroFreeUsing01(byteArrayOf(SOFT_DELETE_INDICATOR))
        val historicKey = packVersionedKey(tableDirs.historicTablePrefix, key.bytes, encodedQualifier, version = versionBytes)
        dataStore.runTransaction { tr ->
            tr.clear(historicKey)
        }

        val beforeDelete = dataStore.execute(
            Log.getChanges(key, toVersion = deleteStatus.version - 1uL, maxVersions = 100u, filterSoftDeleted = false)
        )
        assertFalse(hasSoftDeleteChange(beforeDelete.changes.firstOrNull()?.changes.orEmpty()))
        val atDelete = dataStore.execute(
            Log.getChanges(key, toVersion = deleteStatus.version, maxVersions = 100u, filterSoftDeleted = false)
        )
        assertTrue(hasSoftDeleteChange(atDelete.changes.firstOrNull()?.changes.orEmpty()))

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
    }

    @Test
    fun softDeleteFallbackAppearsInUpdateHistoryScanUpdates() = runTest {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "legacy-soft-delete-history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        val values = Log("legacy-log-fdb-history")
        val key = Log.key(values)

        val addResponse = dataStore.execute(Log.add(key to values))
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(Log.delete(key, hardDelete = false))
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val tableDirs = dataStore.getTableDirs(Log) as HistoricTableDirectories
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val encodedQualifier = encodeZeroFreeUsing01(byteArrayOf(SOFT_DELETE_INDICATOR))
        val historicKey = packVersionedKey(tableDirs.historicTablePrefix, key.bytes, encodedQualifier, version = versionBytes)
        dataStore.runTransaction { tr ->
            tr.clear(historicKey)
        }

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
