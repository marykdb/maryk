package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.deleteFolder
import maryk.test.models.Log
import maryk.test.models.Option.V1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MalformedSoftDeleteCurrentRowRocksDBTest {
    @Test
    fun malformedOverlongCurrentSoftDeleteRowIsIgnoredByGetAndGetChanges() = runTest {
        val folder = createTestDBFolder("malformed-soft-delete-current-row")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Log),
            )

            val values = Log("live-log")
            val addStatus = assertIs<AddSuccess<Log>>(store.execute(Log.add(values)).statuses.single())
            val key = addStatus.key

            val softDeleteKey = key.bytes + SOFT_DELETE_INDICATOR
            store.db.put(
                store.getColumnFamilies(Log).table,
                softDeleteKey,
                HLC.toStorageBytes(HLC(addStatus.version + 1u)) + byteArrayOf(1, 0)
            )

            val getResponse = store.execute(
                Log.get(
                    key,
                    filterSoftDeleted = false,
                )
            )
            assertEquals(1, getResponse.values.size)
            assertFalse(getResponse.values.single().isDeleted)

            val scanResponse = store.execute(
                Log.scan(
                    filterSoftDeleted = false,
                    allowTableScan = true,
                )
            )
            assertEquals(1, scanResponse.values.size)
            assertFalse(scanResponse.values.single().isDeleted)

            val changesResponse = store.execute(
                Log.getChanges(
                    key,
                    maxVersions = 1u,
                    filterSoftDeleted = false,
                )
            )
            assertFalse(
                changesResponse.changes.single().changes.any { versioned ->
                    versioned.changes.any { it is ObjectSoftDeleteChange }
                }
            )

            val changeStatus = assertIs<ChangeSuccess<Log>>(
                store.execute(
                    Log.change(
                        key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )

            val deletedResponse = store.execute(
                Log.get(
                    key,
                    filterSoftDeleted = false,
                )
            )
            assertEquals(1, deletedResponse.values.size)
            assertFalse(changeStatus.version <= addStatus.version)
            assertTrue(deletedResponse.values.single().isDeleted)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedEmptyCurrentSetItemPayloadDoesNotCrashSetChange() = runTest {
        val folder = createTestDBFolder("malformed-empty-set-item-current-row")
        val setValue = LocalDate(2018, 11, 25)

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to TestMarykModel),
            )

            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            uint with 1u
                            bool with true
                            enum with V1
                            int with 1
                            double with 1.0
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            set with setOf(setValue)
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(TestMarykModel)
            val setItemKey = addStatus.key.bytes + TestMarykModel { set.refAt(setValue) }.toStorageByteArray()
            store.db.put(
                columnFamilies.table,
                setItemKey,
                HLC.toStorageBytes(HLC(addStatus.version))
            )

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            SetChange(
                                TestMarykModel { set::ref }.change(
                                    addValues = setOf(setValue)
                                )
                            )
                        )
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
