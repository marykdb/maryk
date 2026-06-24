package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import maryk.deleteFolder
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HistoricUniqueTimeTravelTest {
    @Test
    fun historicUniqueScanSkipsRemovedUniqueValue() = runTest {
        val folder = createTestDBFolder("historic-unique-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            )

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

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun hardDeleteRemovesHistoricUniqueLookupsBeforeDeletion() = runTest {
        val folder = createTestDBFolder("historic-unique-hard-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            )

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

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicIndexStartKeyFallbackAllowsEmptyIndexValue() = runTest {
        val folder = createTestDBFolder("historic-empty-index-start-key")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to EmptyIndexedStringModel),
            )

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
            val columnFamilies = store.getColumnFamilies(EmptyIndexedStringModel)
            val reference = EmptyIndexedStringModel { value::ref }.toStorageByteArray()
            val valueKey = startStatus.key.bytes + reference
            val current = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, current + byteArrayOf(1))

            val response = store.execute(
                EmptyIndexedStringModel.scan(
                    startKey = startStatus.key,
                    toVersion = addStatuses.maxOf { it.version },
                    order = EmptyIndexedStringModel { value::ref }.ascending()
                )
            )

            assertEquals(listOf(startStatus.key), response.values.map { it.key })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}

private object EmptyIndexedStringModel : RootDataModel<EmptyIndexedStringModel>(
    indexes = {
        EmptyIndexedStringModel.run {
            listOf(
                value.ref()
            )
        }
    }
) {
    val value by string(
        index = 1u
    )
}
