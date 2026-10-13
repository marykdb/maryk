package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.deleteFolder
import kotlin.test.Test
import maryk.datastore.test.assertStatusIs
import kotlin.test.assertEquals

class LegacySoftDeleteStateTest {
    @Test
    fun missingDeletionRowKeepsSnapshotStateAndFiltersCorrect() = runTest {
        checkLegacyState(restored = false)
    }

    @Test
    fun missingRestorationRowOverridesOlderDeletionOnlyWithinItsWindow() = runTest {
        checkLegacyState(restored = true)
    }

    private suspend fun checkLegacyState(restored: Boolean) {
        val folder = createTestDBFolder("legacy-soft-delete-state-$restored")
        val store = RocksDBDataStore.open(
            relativePath = folder, keepAllVersions = true, keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to LegacyStateModel),
        )
        try {
            val added = assertStatusIs<AddSuccess<LegacyStateModel>>(
                store.execute(LegacyStateModel.add(LegacyStateModel.create { value with "state" })).statuses.single()
            )
            val deleted = assertStatusIs<DeleteSuccess<LegacyStateModel>>(
                store.execute(LegacyStateModel.delete(added.key, hardDelete = false)).statuses.single()
            ).version
            val markerVersion = if (restored) assertStatusIs<ChangeSuccess<LegacyStateModel>>(
                store.execute(LegacyStateModel.change(added.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
            ).version else deleted
            val columns = store.getColumnFamilies(LegacyStateModel) as HistoricTableColumnFamilies
            store.db.delete(columns.historic.table, added.key.bytes + SOFT_DELETE_INDICATOR + markerVersion.toReversedVersionBytes())
            for ((version, expectedDeleted) in listOf(added.version to false, deleted to true) +
                if (restored) listOf(markerVersion to false) else emptyList()) {
                for (select in listOf(null, LegacyStateModel.graph { emptyList() })) {
                    val get = store.execute(LegacyStateModel.get(added.key, select = select, toVersion = version, filterSoftDeleted = false))
                    val scan = store.execute(LegacyStateModel.scan(select = select, toVersion = version, filterSoftDeleted = false, allowTableScan = true))
                    assertEquals(expectedDeleted, get.values.single().isDeleted, "Get at $version, restored=$restored, select=$select")
                    assertEquals(expectedDeleted, scan.values.single().isDeleted, "Scan at $version, restored=$restored, select=$select")
                    val active = store.execute(LegacyStateModel.get(added.key, select = select, toVersion = version))
                    assertEquals(if (expectedDeleted) 0 else 1, active.values.size)
                    val activeScan = store.execute(LegacyStateModel.scan(select = select, toVersion = version, allowTableScan = true))
                    assertEquals(if (expectedDeleted) 0 else 1, activeScan.values.size)
                }
            }
            if (restored) {
                val empty = LegacyStateModel.graph { emptyList() }
                for (response in listOf(
                    store.execute(LegacyStateModel.getChanges(added.key, fromVersion = deleted, toVersion = markerVersion, select = empty)),
                    store.execute(LegacyStateModel.scanChanges(fromVersion = deleted, toVersion = markerVersion, select = empty))
                )) {
                    assertEquals(listOf(markerVersion), response.changes.flatMap { it.changes }.map { it.version })
                    assertEquals(listOf(ObjectSoftDeleteChange(false)), response.changes.single().changes.single().changes)
                }
                val history = store.execute(LegacyStateModel.getUpdateHistory(added.key, fromVersion = markerVersion, toVersion = markerVersion))
                assertEquals(listOf(markerVersion), history.updates.map { it.version })
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}

private object LegacyStateModel : RootDataModel<LegacyStateModel>(minimumKeyScanByteRange = 0u) {
    val value by string(1u)
}
