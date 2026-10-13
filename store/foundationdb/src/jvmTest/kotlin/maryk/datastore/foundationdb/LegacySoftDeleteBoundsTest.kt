package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.models.graph
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.core.clock.HLC
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LegacySoftDeleteBoundsTest {
    @Test
    fun boundedChangesAndUpdatesRecoverLegacyDeletion() = runTest {
        checkLegacyMarker(restored = false)
    }

    @Test
    fun missingRestorationRowDoesNotKeepAnOlderDeletionOrExceedMaxVersions() = runTest {
        checkLegacyMarker(restored = true)
    }

    private suspend fun checkLegacyMarker(restored: Boolean) {
        for (historyIndex in listOf(false, true)) {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "LegacySoftDeleteBoundsTest", Uuid.random().toString()),
                keepAllVersions = true,
                keepUpdateHistoryIndex = historyIndex,
                dataModelsById = mapOf(1u to LegacyMarkerModel),
            )
            try {
                val added = assertIs<AddSuccess<LegacyMarkerModel>>(
                    store.execute(LegacyMarkerModel.add(LegacyMarkerModel.create { value with "haha" })).statuses.single()
                )
                val deleted = assertIs<DeleteSuccess<LegacyMarkerModel>>(
                    store.execute(LegacyMarkerModel.delete(added.key, hardDelete = false)).statuses.single()
                ).version
                val markerVersion = if (restored) {
                    assertIs<ChangeSuccess<LegacyMarkerModel>>(
                        store.execute(LegacyMarkerModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
                    ).version
                } else deleted
                val columns = store.getTableDirs(LegacyMarkerModel) as HistoricTableDirectories
                store.runTransaction { tr ->
                tr.clear(packVersionedKey(columns.historicTablePrefix, added.key.bytes,
                    encodeZeroFreeUsing01(byteArrayOf(SOFT_DELETE_INDICATOR)), version = HLC.toStorageBytes(HLC(markerVersion))))
            }
                val empty = LegacyMarkerModel.graph { emptyList() }
                for (upper in listOf<ULong?>(null, markerVersion, deleted).distinct()) {
                    for (maxVersions in listOf(1u, 100u)) {
                        val expected = buildList {
                            if (!restored || upper == deleted || maxVersions > 1u) add(deleted to ObjectSoftDeleteChange(true))
                            if (restored && (upper == null || upper >= markerVersion)) add(markerVersion to ObjectSoftDeleteChange(false))
                        }
                        val changes = listOf(
                            store.execute(LegacyMarkerModel.getChanges(added.key, select = empty, fromVersion = deleted, toVersion = upper, maxVersions = maxVersions, filterSoftDeleted = false)),
                            store.execute(LegacyMarkerModel.scanChanges(select = empty, fromVersion = deleted, toVersion = upper, maxVersions = maxVersions, filterSoftDeleted = false))
                        )
                        for (response in changes) {
                            assertEquals(expected, response.changes.flatMap { it.changes }.flatMap { version ->
                                version.changes.filterIsInstance<ObjectSoftDeleteChange>().map { version.version to it }
                            }, "restored=$restored historyIndex=$historyIndex upper=$upper maxVersions=$maxVersions")
                        }
                        val updates = listOf(
                            store.execute(LegacyMarkerModel.getUpdates(added.key, select = empty, fromVersion = deleted, toVersion = upper, maxVersions = maxVersions, filterSoftDeleted = false)),
                            store.execute(LegacyMarkerModel.scanUpdates(select = empty, fromVersion = deleted, toVersion = upper, maxVersions = maxVersions, filterSoftDeleted = false))
                        )
                        for (response in updates) {
                            assertEquals(expected, response.updates.filterIsInstance<ChangeUpdate<LegacyMarkerModel>>().flatMap { version ->
                                version.changes.filterIsInstance<ObjectSoftDeleteChange>().map { version.version to it }
                            }, "restored=$restored historyIndex=$historyIndex upper=$upper maxVersions=$maxVersions")
                        }
                    }
                }
            } finally {
                store.close()
            }
        }
    }
}

private object LegacyMarkerModel : RootDataModel<LegacyMarkerModel>(minimumKeyScanByteRange = 0u) {
    val value by string(1u)
}
