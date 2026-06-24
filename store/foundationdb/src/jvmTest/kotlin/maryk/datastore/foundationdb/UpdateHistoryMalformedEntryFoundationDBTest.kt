
package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class UpdateHistoryMalformedEntryFoundationDBTest {
    @Test
    fun scanUpdateHistorySkipsLongMalformedDuplicateEntry() = runTest {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "update-history-malformed-duplicate", Uuid.random().toString()),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to TestMarykModel),
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

            val change = Change(TestMarykModel { int::ref } with 6)
            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(addStatus.key.change(change))
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(TestMarykModel) as HistoricTableDirectories
            store.runTransaction { tr ->
                tr.set(
                    packKey(
                        tableDirs.updateHistoryPrefix!!,
                        changeStatus.version.toReversedVersionBytes(),
                        addStatus.key.bytes + byteArrayOf(1)
                    ),
                    byteArrayOf()
                )
            }

            val response = store.execute(
                TestMarykModel.scanUpdateHistory(
                    fromVersion = changeStatus.version,
                    limit = 10u
                )
            )

            assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
            val updates = response.updates.map { assertIs<ChangeUpdate<TestMarykModel>>(it) }
            assertEquals(1, updates.size)
            assertEquals(changeStatus.version, updates.single().version)
            assertEquals(addStatus.key, updates.single().key)
            assertEquals(listOf(change), updates.single().changes)
        } finally {
            store.close()
        }
    }
}
