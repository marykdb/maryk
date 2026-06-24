package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.int
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HistoricIndexTimeTravelTest {
    @Test
    fun historicIndexScanSkipsRemovedPrimaryQualifier() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-scan", Uuid.random().toString()),
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
                        }
                    )
                ).statuses.single()
            )

            val beforeChange = store.execute(
                TestMarykModel.scan(
                    where = Equals(int.ref() with 5),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, beforeChange.values.size)
            assertEquals(addStatus.key, beforeChange.values.single().key)

            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            Change(
                                int.ref() with 4
                            )
                        )
                    )
                ).statuses.single()
            )

            val afterChange = store.execute(
                TestMarykModel.scan(
                    where = Equals(int.ref() with 5),
                    toVersion = changeStatus.version
                )
            )
            assertEquals(0, afterChange.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicUniqueScanSkipsRemovedUniqueValue() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-unique-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val beforeChange = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, beforeChange.values.size)
            assertEquals(addStatus.key, beforeChange.values.single().key)

            val changeStatus = assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(
                                email.ref() with "new@test.com"
                            )
                        )
                    )
                ).statuses.single()
            )

            val afterChange = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = changeStatus.version
                )
            )
            assertEquals(0, afterChange.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun hardDeleteRemovesHistoricUniqueLookupsBeforeDeletion() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-unique-hard-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val changeStatus = assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(
                                email.ref() with "new@test.com"
                            )
                        )
                    )
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.delete(addStatus.key, hardDelete = true)
                ).statuses.single()
            )

            assertEquals(0, store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = addStatus.version
                )
            ).values.size)

            assertEquals(0, store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "new@test.com"),
                    toVersion = changeStatus.version
                )
            ).values.size)

            assertEquals(0, store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = deleteStatus.version
                )
            ).values.size)

            assertEquals(0, store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "new@test.com"),
                    toVersion = deleteStatus.version
                )
            ).values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicGetDistinguishesEmptyStringFromDeleteTombstone() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-empty-string-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with ""
                        }
                    )
                ).statuses.single()
            )

            val beforeDelete = store.execute(
                UniqueModel.get(
                    addStatus.key,
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, beforeDelete.values.size)
            assertEquals("", beforeDelete.values.single().values { email })

            val deleteStatus = assertIs<DeleteSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.delete(addStatus.key)
                ).statuses.single()
            )

            val afterDelete = store.execute(
                UniqueModel.get(
                    addStatus.key,
                    toVersion = deleteStatus.version
                )
            )
            assertEquals(0, afterDelete.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicIndexStartKeyFallbackAllowsEmptyIndexValue() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-empty-index-start-key", Uuid.random().toString()),
            dataModelsById = mapOf(1u to EmptyIndexedStringModel),
            keepAllVersions = true,
        )

        try {
            val addStatuses = store.execute(
                EmptyIndexedStringModel.add(
                    EmptyIndexedStringModel.create {
                        value with ""
                    },
                    EmptyIndexedStringModel.create {
                        value with ""
                    }
                )
            ).statuses.map { assertIs<AddSuccess<EmptyIndexedStringModel>>(it) }

            val startStatus = addStatuses.maxWith { left, right -> left.key.bytes compareTo right.key.bytes }
            val tableDirs = store.getTableDirs(EmptyIndexedStringModel)
            val reference = EmptyIndexedStringModel { value::ref }.toStorageByteArray()
            val valueKey = packKey(tableDirs.tablePrefix, startStatus.key.bytes + reference)

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

            val response = store.execute(
                EmptyIndexedStringModel.scan(
                    startKey = startStatus.key,
                    toVersion = addStatuses.maxOf { it.version },
                    order = EmptyIndexedStringModel { value::ref }.ascending()
                )
            )

            assertEquals(listOf(startStatus.key), response.values.map { it.key })
        } finally {
            store.close()
        }
    }

    @Test
    fun hardDeleteCleanupFallsBackToModelUniqueDefinitionsWhenMarkerIsMissing() = runTest(timeout = 3.minutes) {
        val directory = listOf("maryk", "test", "historic-unique-missing-marker", Uuid.random().toString())
        lateinit var key: Key<UniqueModel>

        FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directory,
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        ).let { store ->
            try {
                key = assertIs<AddSuccess<UniqueModel>>(
                    store.execute(
                        UniqueModel.add(
                            UniqueModel.create {
                                email with "markerless@test.com"
                            }
                        )
                    ).statuses.single()
                ).key

                val tableDirs = store.getTableDirs(UniqueModel)
                val uniqueDefinitionMarker = byteArrayOf(0) + UniqueModel { email::ref }.toStorageByteArray()
                store.runTransaction { tr ->
                    tr.clear(packKey(tableDirs.uniquePrefix, uniqueDefinitionMarker))
                }
            } finally {
                store.close()
            }
        }

        val reopened = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directory,
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )
        try {
            assertIs<DeleteSuccess<UniqueModel>>(
                reopened.execute(
                    UniqueModel.delete(key, hardDelete = true)
                ).statuses.single()
            )

            assertIs<AddSuccess<UniqueModel>>(
                reopened.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "markerless@test.com"
                        }
                    )
                ).statuses.single()
            )
        } finally {
            reopened.close()
        }
    }
}

object EmptyIndexedStringModel : RootDataModel<EmptyIndexedStringModel>(
    indexes = {
        EmptyIndexedStringModel.run {
            listOf(value.ref())
        }
    }
) {
    val value by string(1u)
}
