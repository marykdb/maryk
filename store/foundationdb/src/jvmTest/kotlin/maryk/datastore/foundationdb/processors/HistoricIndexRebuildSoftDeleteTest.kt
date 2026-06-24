package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HistoricIndexRebuildSoftDeleteTest {
    @Test
    fun rebuildSkipsCurrentIndexRowsForMalformedKeyMetadata() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-rebuild-malformed-key", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val indexable = TestMarykModel { int::ref }
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(TestMarykModel))
            val keyRow = packKey(tableDirs.keysPrefix, addStatus.key.bytes)

            store.runTransaction { tr ->
                val current = tr.get(keyRow).awaitResult()!!
                tr.set(keyRow, current + byteArrayOf(1))
            }

            deleteCompleteIndexContents(store.tc, tableDirs, indexable)
            walkDataRecordsAndFillIndex(store.tc, tableDirs, listOf(indexable))

            val currentScan = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )
            assertEquals(0, currentScan.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildSkipsCurrentIndexRowsForMalformedCurrentValueVersion() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-rebuild-malformed-current-value", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val indexable = TestMarykModel { int::ref }
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(TestMarykModel))
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + TestMarykModel { int::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

            deleteCompleteIndexContents(store.tc, tableDirs, indexable)
            walkDataRecordsAndFillIndex(store.tc, tableDirs, listOf(indexable))

            val currentScan = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )
            assertEquals(0, currentScan.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildSkipsCurrentIndexRowsForMalformedLatestVersionMarker() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-rebuild-malformed-latest", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val indexable = TestMarykModel { int::ref }
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(TestMarykModel))
            val latestKey = packKey(tableDirs.tablePrefix, addStatus.key.bytes)

            store.runTransaction { tr ->
                val current = tr.get(latestKey).awaitResult()!!
                tr.set(latestKey, current + byteArrayOf(1))
            }

            deleteCompleteIndexContents(store.tc, tableDirs, indexable)
            walkDataRecordsAndFillIndex(store.tc, tableDirs, listOf(indexable))

            val currentScan = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )
            assertEquals(0, currentScan.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildPreservesSoftDeleteRemovalVersion() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-rebuild-soft-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key)
                ).statuses.single()
            )

            val indexable = TestMarykModel { int::ref }
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(TestMarykModel))

            deleteCompleteIndexContents(store.tc, tableDirs, indexable)
            walkDataRecordsAndFillIndex(store.tc, tableDirs, listOf(indexable))

            val postDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = deleteStatus.version
                )
            )
            assertEquals(0, postDelete.values.size)

            val preDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, preDelete.values.size)
        } finally {
            store.close()
        }
    }
}
