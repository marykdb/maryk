@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SoftDeleteLegacyTimeTravelTest {
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

        val changesResponse = dataStore.execute(
            Log.getChanges(
                key,
                maxVersions = 100u,
                filterSoftDeleted = false,
            )
        )
        assertTrue(hasSoftDeleteChange(changesResponse.changes.firstOrNull()?.changes.orEmpty()))

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

        val getUpdatesResponse = dataStore.execute(
            Log.getUpdates(key, maxVersions = 100u, filterSoftDeleted = false)
        )
        assertTrue(hasSoftDeleteUpdate(getUpdatesResponse.updates))

        val scanUpdatesResponse = dataStore.execute(
            Log.scanUpdates(
                startKey = key,
                includeStart = true,
                limit = 1u,
                maxVersions = 100u,
                filterSoftDeleted = false
            )
        )
        assertTrue(hasSoftDeleteUpdate(scanUpdatesResponse.updates))

        dataStore.close()
    }
}

private fun hasSoftDeleteChange(changes: List<maryk.core.query.changes.VersionedChanges>): Boolean =
    changes.any { versioned ->
        versioned.changes.any { it is ObjectSoftDeleteChange }
    }

private fun <DM : maryk.core.models.IsRootDataModel> hasSoftDeleteUpdate(
    updates: List<maryk.core.query.responses.updates.IsUpdateResponse<DM>>
): Boolean =
    updates.filterIsInstance<ChangeUpdate<DM>>().any { update ->
        update.changes.any { it is ObjectSoftDeleteChange }
    }
