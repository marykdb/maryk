package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
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
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.test.NullableUniqueModel
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import maryk.foundationdb.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

@Suppress("UNCHECKED_CAST")
class MalformedUniqueValueFoundationDBTest {
    @Test
    fun malformedCurrentUniqueValueIsSkippedDuringUniqueScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-value", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(UniqueModel)
            val reference = UniqueModel { email::ref }.toStorageByteArray()
            val prefix = packKey(tableDirs.uniquePrefix, reference)

            store.runTransaction { tr ->
                val rows = tr.getRange(Range.startsWith(prefix)).asList().awaitResult()
                check(rows.size == 1) { "Expected one unique entry for malformed-value test" }
                val row = rows.single()
                tr.set(row.key, row.value + byteArrayOf(1))
            }

            val scanResponse = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com")
                )
            )

            assertIs<FetchByUniqueKey>(scanResponse.dataFetchType)
            assertEquals(0, scanResponse.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedHistoricUniqueValueIsSkippedDuringHistoricUniqueScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-historic-unique-value", Uuid.random().toString()),
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

            val tableDirs = store.getTableDirs(UniqueModel) as HistoricTableDirectories
            val reference = UniqueModel { email::ref }.toStorageByteArray()
            val prefix = packKey(tableDirs.historicUniquePrefix, encodeZeroFreeUsing01(reference))

            store.runTransaction { tr ->
                val rows = tr.getRange(Range.startsWith(prefix)).asList().awaitResult()
                check(rows.size == 1) { "Expected one historic unique entry for malformed-value test" }
                val row = rows.single()
                tr.set(row.key, row.value + byteArrayOf(1))
            }

            val scanResponse = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = addStatus.version
                )
            )

            assertIs<FetchByUniqueKey>(scanResponse.dataFetchType)
            assertEquals(0, scanResponse.values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedLatestHistoricUniqueValueDoesNotHideOlderValidValue() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-latest-historic-unique-value", Uuid.random().toString()),
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

            val tableDirs = store.getTableDirs(UniqueModel) as HistoricTableDirectories
            val malformedVersion = addStatus.version + 1uL
            store.runTransaction { tr ->
                tr.set(
                    packVersionedKey(
                        tableDirs.historicUniquePrefix,
                        encodeZeroFreeUsing01(uniqueReference(store, "old@test.com")),
                        version = HLC.toStorageBytes(HLC(malformedVersion))
                    ),
                    ByteArray(UniqueModel.Meta.keyByteSize + 1)
                )
            }

            val scanResponse = store.execute(
                UniqueModel.scan(
                    where = Equals(email.ref() with "old@test.com"),
                    toVersion = malformedVersion
                )
            )

            assertIs<FetchByUniqueKey>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(addStatus.key, scanResponse.values.single().key)
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentUniqueValueDoesNotBlockAddOrChange() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-conflict", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val malformedValue = ByteArray(VERSION_BYTE_SIZE + UniqueModel.Meta.keyByteSize + 1)

            store.runTransaction { tr ->
                tr.set(
                    packKey(store.getTableDirs(UniqueModel).uniquePrefix, uniqueReference(store, "ghost-add@test.com")),
                    malformedValue
                )
            }

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

            store.runTransaction { tr ->
                tr.set(
                    packKey(store.getTableDirs(UniqueModel).uniquePrefix, uniqueReference(store, "ghost-change@test.com")),
                    malformedValue
                )
            }

            assertIs<ChangeSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.change(
                        addStatus.key.change(
                            Change(email.ref() with "ghost-change@test.com")
                        )
                    )
                ).statuses.single()
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnHardDelete() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-property-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "delete@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(UniqueModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnChange() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-property-change", Uuid.random().toString()),
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

            val tableDirs = store.getTableDirs(UniqueModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedNullableUniquePropertyValueDoesNotLeaveStaleUniqueOnDeleteByReference() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-nullable-unique-property-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to NullableUniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<NullableUniqueModel>>(
                store.execute(
                    NullableUniqueModel.add(
                        NullableUniqueModel.create {
                            email with "old@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(NullableUniqueModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + NullableUniqueModel { email::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueDoesNotLeaveStaleUniqueOnSoftDelete() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-property-soft-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "soft@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(UniqueModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentUniquePropertyValueRestoresCanonicalUniqueOnUndelete() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-unique-property-undelete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            val addStatus = assertIs<AddSuccess<UniqueModel>>(
                store.execute(
                    UniqueModel.add(
                        UniqueModel.create {
                            email with "undelete@test.com"
                        }
                    )
                ).statuses.single()
            )

            val tableDirs = store.getTableDirs(UniqueModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + UniqueModel { email::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    private fun uniqueReference(store: FoundationDBDataStore, emailValue: String): ByteArray {
        val reference = UniqueModel { email::ref }.toStorageByteArray()
        val definition = UniqueModel.email.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>
        val valueBytes = definition.toStorageBytes(emailValue as Comparable<Any>)
        val rawUniqueValue = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + valueBytes
        val uniqueValue = store.mapUniqueValueBytes(1u, reference, rawUniqueValue)

        return reference + uniqueValue
    }
}
