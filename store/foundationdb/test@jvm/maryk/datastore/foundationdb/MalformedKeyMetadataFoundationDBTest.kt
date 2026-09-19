package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MalformedKeyMetadataFoundationDBTest {
    @Test
    fun malformedOverlongKeyMetadataIsIgnoredByGetAndScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-key-metadata", Uuid.random().toString()),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to UniqueModel),
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "test@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(UniqueModel)
            val keyRow = packKey(tableDirs.keysPrefix, addStatus.key.bytes)
            val latestRow = packKey(tableDirs.tablePrefix, addStatus.key.bytes)
            store.runTransaction { tr ->
                val current = tr.get(keyRow).awaitResult()!!
                tr.set(keyRow, current + byteArrayOf(1))
            }

            assertEquals(
                1,
                store.execute(
                    UniqueModel.get(addStatus.key, toVersion = addStatus.version)
                ).values.size
            )
            assertEquals(
                1,
                store.execute(
                    UniqueModel.scan(
                        toVersion = addStatus.version,
                        allowTableScan = true
                    )
                ).values.size
            )
            assertEquals(
                1,
                store.execute(
                    UniqueModel.scan(
                        where = Equals(email.ref() with "test@test.com"),
                        toVersion = addStatus.version
                    )
                ).values.size
            )
            assertEquals(
                listOf(addStatus.key),
                assertIs<OrderedKeysUpdate<UniqueModel>>(
                    store.execute(
                        UniqueModel.getUpdates(addStatus.key, toVersion = addStatus.version)
                    ).updates.first()
                ).keys
            )
            assertEquals(
                listOf(addStatus.key),
                assertIs<OrderedKeysUpdate<UniqueModel>>(
                    store.execute(
                        UniqueModel.scanUpdates(
                            where = Equals(email.ref() with "test@test.com"),
                            toVersion = addStatus.version
                        )
                    ).updates.first()
                ).keys
            )
            assertEquals(
                1,
                store.execute(
                    UniqueModel.getChanges(addStatus.key, toVersion = addStatus.version)
                ).changes.size
            )
            assertEquals(
                1,
                store.execute(
                    UniqueModel.scanUpdateHistory(
                        toVersion = addStatus.version,
                        fromVersion = addStatus.version,
                        limit = 1u
                    )
                ).updates.size
            )

            assertEquals(0, store.execute(UniqueModel.get(addStatus.key)).values.size)
            assertEquals(
                0,
                store.execute(
                    UniqueModel.scan(
                        where = Equals(email.ref() with "test@test.com")
                    )
                ).values.size
            )
            assertIs<DoesNotExist<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(email.ref() with "other@test.com")
                        )
                    )
                ).statuses.single()
            )
            assertIs<DoesNotExist<UniqueModel>>(
                store.execute(
                    UniqueModel.delete(addStatus.key)
                ).statuses.single()
            )

            store.runTransaction { tr ->
                tr.set(keyRow, tr.get(keyRow).awaitResult()!!.copyOfRange(0, ULong.SIZE_BYTES))
                val latest = tr.get(latestRow).awaitResult()!!
                tr.set(latestRow, latest + byteArrayOf(1))
            }

            assertEquals(
                listOf(addStatus.key),
                assertIs<OrderedKeysUpdate<UniqueModel>>(
                    store.execute(
                        UniqueModel.getUpdates(addStatus.key, toVersion = addStatus.version)
                    ).updates.first()
                ).keys
            )
            assertEquals(
                listOf(addStatus.key),
                assertIs<OrderedKeysUpdate<UniqueModel>>(
                    store.execute(
                        UniqueModel.scanUpdates(
                            where = Equals(email.ref() with "test@test.com"),
                            toVersion = addStatus.version
                        )
                    ).updates.first()
                ).keys
            )

            assertIs<DoesNotExist<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(email.ref() with "latest@test.com")
                        )
                    )
                ).statuses.single()
            )
            assertEquals(
                emptyList(),
                assertIs<OrderedKeysUpdate<UniqueModel>>(
                    store.execute(
                        UniqueModel.getUpdates(addStatus.key)
                    ).updates.first()
                ).keys
            )
        } finally {
            store.close()
        }
    }
}
