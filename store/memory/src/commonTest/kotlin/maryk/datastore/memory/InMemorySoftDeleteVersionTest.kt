package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemorySoftDeleteVersionTest {
    @Test
    fun softDeleteAdvancesRecordVersionForReadsUpdatesAndVersionedChanges() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        try {
            val addStatus = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.add(
                        SimpleMarykModel.create { value with "hasoftdeleteversion" }
                    )
                ).statuses.single()
            )
            val deleteStatus = assertIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(addStatus.key)).statuses.single()
            )

            val current = dataStore.execute(
                SimpleMarykModel.get(addStatus.key, filterSoftDeleted = false)
            ).values.single()
            assertTrue(current.isDeleted)
            assertEquals(deleteStatus.version, current.lastVersion)

            val scanned = dataStore.execute(
                SimpleMarykModel.scan(filterSoftDeleted = false, allowTableScan = true)
            ).values.single()
            assertTrue(scanned.isDeleted)
            assertEquals(deleteStatus.version, scanned.lastVersion)

            val updates = dataStore.execute(
                SimpleMarykModel.getUpdates(addStatus.key, filterSoftDeleted = false)
            )
            assertEquals(
                deleteStatus.version,
                assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates.first()).version
            )
            assertEquals(
                deleteStatus.version,
                assertIs<ChangeUpdate<SimpleMarykModel>>(updates.updates.last()).version
            )

            val undeleteStatus = assertIs<ChangeSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.change(
                        addStatus.key.change(
                            ObjectSoftDeleteChange(false),
                            lastVersion = deleteStatus.version
                        )
                    )
                ).statuses.single()
            )

            val restored = dataStore.execute(
                SimpleMarykModel.get(addStatus.key, filterSoftDeleted = false)
            ).values.single()
            assertFalse(restored.isDeleted)
            assertEquals(undeleteStatus.version, restored.lastVersion)

            val historical = dataStore.execute(
                SimpleMarykModel.get(
                    addStatus.key,
                    toVersion = addStatus.version,
                    filterSoftDeleted = false
                )
            ).values.single()
            assertFalse(historical.isDeleted)
            assertEquals(addStatus.version, historical.lastVersion)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun replayingOlderSoftDeleteDoesNotDowngradeRecordVersion() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        try {
            val addStatus = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.add(
                        SimpleMarykModel.create { value with "hareplayedsoftdelete" }
                    )
                ).statuses.single()
            )
            val deleteStatus = assertIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(addStatus.key)).statuses.single()
            )

            val replayResponse = dataStore.processUpdate(
                UpdateResponse(
                    dataModel = SimpleMarykModel,
                    update = RemovalUpdate(
                        key = addStatus.key,
                        version = addStatus.version,
                        reason = SoftDelete
                    )
                )
            )
            assertIs<DeleteResponse<SimpleMarykModel>>(replayResponse.result).statuses.single().let {
                assertIs<DeleteSuccess<SimpleMarykModel>>(it)
            }

            val updates = dataStore.execute(
                SimpleMarykModel.getUpdates(addStatus.key, filterSoftDeleted = false)
            )
            assertEquals(
                deleteStatus.version,
                assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates.first()).version
            )

            assertIs<ChangeSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.change(
                        addStatus.key.change(
                            ObjectSoftDeleteChange(false),
                            lastVersion = deleteStatus.version
                        )
                    )
                ).statuses.single()
            )
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesWithUpdateHistoryIndexReportsSoftDeleteVersion() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )

        try {
            val addStatus = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.add(
                        SimpleMarykModel.create { value with "hascanupdatessoftdelete" }
                    )
                ).statuses.single()
            )
            val deleteStatus = assertIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(addStatus.key)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(filterSoftDeleted = false)
            )
            assertEquals(
                deleteStatus.version,
                assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates.first()).version
            )
            assertEquals(
                deleteStatus.version,
                assertIs<ChangeUpdate<SimpleMarykModel>>(updates.updates.last()).version
            )
        } finally {
            dataStore.close()
        }
    }
}
