package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.test.NullableUniqueModel
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import maryk.deleteFolder
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("UNCHECKED_CAST")
class MalformedUniqueValueRocksDBTest {
    @Test
    fun malformedCurrentUniqueValueIsSkippedDuringUniqueScan() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-value")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            )

            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val reference = UniqueModel { email::ref }.toStorageByteArray()

            store.db.newIterator(columnFamilies.unique).use { iterator ->
                iterator.seek(reference)
                check(iterator.isValid() && iterator.key().matchesRangePart(0, reference)) {
                    "Expected unique entry for injected malformed-value test"
                }
                store.db.put(columnFamilies.unique, iterator.key(), iterator.value() + byteArrayOf(1))
            }

            val scanResponse = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com")
                )
            )

            assertIs<FetchByUniqueKey>(scanResponse.dataFetchType)
            assertEquals(0, scanResponse.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedHistoricUniqueValueIsSkippedDuringHistoricUniqueScan() = runTest {
        val folder = createTestDBFolder("malformed-historic-unique-value")

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

            val columnFamilies = store.getColumnFamilies(UniqueModel) as HistoricTableColumnFamilies
            val reference = UniqueModel { email::ref }.toStorageByteArray()

            store.db.newIterator(columnFamilies.historic.unique).use { iterator ->
                iterator.seek(reference)
                check(iterator.isValid() && iterator.key().matchesRangePart(0, reference)) {
                    "Expected historic unique entry for injected malformed-value test"
                }
                store.db.put(columnFamilies.historic.unique, iterator.key(), iterator.value() + byteArrayOf(1))
            }

            val scanResponse = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = addStatus.version
                )
            )

            assertIs<FetchByUniqueKey>(scanResponse.dataFetchType)
            assertEquals(0, scanResponse.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentUniqueValueDoesNotBlockAddOrChange() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-conflict")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to UniqueModel),
            )

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val malformedValue = ByteArray(VERSION_BYTE_SIZE + UniqueModel.Meta.keyByteSize + 1)

            store.db.put(
                columnFamilies.unique,
                uniqueReference(store, "ghost-add@test.com"),
                malformedValue
            )

            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "ghost-add@test.com"
                        }
                    )
                ).statuses.single()
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

            store.db.put(
                columnFamilies.unique,
                uniqueReference(store, "ghost-change@test.com"),
                malformedValue
            )

            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(email.ref() with "ghost-change@test.com")
                        )
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnHardDelete() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-property-delete")

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
                            email with "delete@test.com"
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val valueKey = addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<DeleteSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.delete(addStatus.key, hardDelete = true)
                ).statuses.single()
            )

            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "delete@test.com"
                        }
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnChange() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-property-change")

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

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val valueKey = addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(email.ref() with "new@test.com")
                        )
                    )
                ).statuses.single()
            )

            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedNullableUniquePropertyValueDoesNotLeaveStaleUniqueOnDeleteByReference() = runTest {
        val folder = createTestDBFolder("malformed-nullable-unique-property-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to NullableUniqueModel),
            )

            val addStatus = assertIs<AddSuccess<NullableUniqueModel>>(
                store.execute(
                    NullableUniqueModel.add(
                        NullableUniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(NullableUniqueModel)
            val valueKey = addStatus.key.bytes + NullableUniqueModel { email::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<NullableUniqueModel>>(
                store.execute(
                    NullableUniqueModel.change(
                        addStatus.key.change(
                            Change(NullableUniqueModel { email::ref } with null)
                        )
                    )
                ).statuses.single()
            )

            assertIs<AddSuccess<NullableUniqueModel>>(
                store.execute(
                    NullableUniqueModel.add(
                        NullableUniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnSoftDelete() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-property-soft-delete")

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
                            email with "soft@test.com"
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val valueKey = addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )

            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "soft@test.com"
                        }
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueRestoresCanonicalUniqueOnUndelete() = runTest {
        val folder = createTestDBFolder("malformed-current-unique-property-undelete")

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
                            email with "undelete@test.com"
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(UniqueModel)
            val valueKey = addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )
            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(false))
                    )
                ).statuses.single()
            )

            assertIs<ValidationFail<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "undelete@test.com"
                        }
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    private fun uniqueReference(store: RocksDBDataStore, emailValue: String): ByteArray {
        val reference = UniqueModel { email::ref }.toStorageByteArray()
        val definition = UniqueModel.email.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>
        val valueBytes = definition.toStorageBytes(emailValue as Comparable<Any>)
        val rawUniqueValue = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + valueBytes
        val uniqueValue = store.mapUniqueValueBytes(1u, reference, rawUniqueValue)

        return reference + uniqueValue
    }
}
