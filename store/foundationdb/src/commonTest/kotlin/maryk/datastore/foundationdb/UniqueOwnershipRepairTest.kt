package maryk.datastore.foundationdb

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.RestoreVersionRequest
import maryk.datastore.shared.TypeIndicator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private object UniqueRepairRecord : RootDataModel<UniqueRepairRecord>() {
    val label by string(1u, unique = true)
}

class UniqueOwnershipRepairTest {
    @Test
    fun undeleteWithChangedUniqueValueWorksAcrossRepairScanBoundary() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) for (otherClaims in listOf(512, 513)) {
            withRecords(history, otherClaims) { store, records ->
                val key = records.first().key
                store.execute(UniqueRepairRecord.delete(key))
                val response = store.execute(UniqueRepairRecord.change(key.change(
                    ObjectSoftDeleteChange(false),
                    Change(UniqueRepairRecord.ref { label } with "undeleted"),
                )))
                assertIs<ChangeSuccess<UniqueRepairRecord>>(response.statuses.single())
                assertEquals("undeleted", store.execute(UniqueRepairRecord.get(key)).values.single().values { label })
                assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "claim-0"))
                assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "undeleted"))
            }
        }
    }

    @Test
    fun versionRestoreWithChangedUniqueValueWorksAcrossRepairScanBoundary() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) for (otherClaims in listOf(512, 513)) {
            withRecords(history, otherClaims) { store, records ->
                val key = records.first().key
                store.restoreVersion(restore(key, records.maxOf { it.version } + 1uL, "restored"))
                assertEquals("restored", store.execute(UniqueRepairRecord.get(key)).values.single().values { label })
                assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "claim-0"))
                assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "restored"))
            }
        }
    }

    @Test
    fun deleteThenUndeleteInOneChangeCanReplaceAUniqueValue() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) withRecords(history) { store, records ->
            val key = records.first().key
            val response = store.execute(UniqueRepairRecord.change(key.change(
                ObjectSoftDeleteChange(true),
                ObjectSoftDeleteChange(false),
                Change(UniqueRepairRecord.ref { label } with "replacement"),
            )))
            assertIs<ChangeSuccess<UniqueRepairRecord>>(response.statuses.single())
            assertEquals("replacement", store.execute(UniqueRepairRecord.get(key)).values.single().values { label })
            assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "claim-0"))
        }
    }

    @Test
    fun conflictingUndeleteLeavesTheDeletedRecordUnchanged() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) withRecords(history) { store, records ->
            val key = records.first().key
            store.execute(UniqueRepairRecord.delete(key))
            val before = store.execute(UniqueRepairRecord.get(key, filterSoftDeleted = false)).values.single()
            val response = store.execute(UniqueRepairRecord.change(key.change(
                ObjectSoftDeleteChange(false),
                Change(UniqueRepairRecord.ref { label } with "claim-1"),
            )))
            assertIs<ValidationFail<UniqueRepairRecord>>(response.statuses.single())
            assertEquals(before, store.execute(UniqueRepairRecord.get(key, filterSoftDeleted = false)).values.single())
            assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "claim-0"))
            assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "claim-1"))
        }
    }

    @Test
    fun conflictingVersionRestoreRollsBackReleasedOwnership() = runBoundedIntegrationTest {
        for (history in listOf(false, true)) withRecords(history) { store, records ->
            val key = records.first().key
            val before = store.execute(UniqueRepairRecord.get(key)).values.single()
            assertFailsWith<RequestException> {
                store.restoreVersion(restore(key, records.maxOf { it.version } + 1uL, "claim-1"))
            }
            assertEquals(before, store.execute(UniqueRepairRecord.get(key)).values.single())
            assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "claim-0"))
            assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "claim-1"))
        }
    }

    @Test
    fun historicSourceStillRepairsDamagedCurrentUniquePayloads() = runBoundedIntegrationTest {
        withRecords(history = true) { store, records ->
            val key = records.first().key
            assertIs<ChangeSuccess<UniqueRepairRecord>>(store.execute(UniqueRepairRecord.change(key.change(
                Change(UniqueRepairRecord.ref { label } with "zzzz-original"),
            ))).statuses.single())
            damageCurrentPayload(store, key)
            val response = store.execute(UniqueRepairRecord.change(key.change(
                Change(UniqueRepairRecord.ref { label } with "repaired"),
            )))
            assertIs<ChangeSuccess<UniqueRepairRecord>>(response.statuses.single())
            assertEquals("repaired", store.execute(UniqueRepairRecord.get(key)).values.single().values { label })
            assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "zzzz-original"))
            assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "repaired"))
        }
    }

    @Test
    fun unexpectedMissingOwnershipKeepsTheRepairScanBounded() = runBoundedIntegrationTest {
        withRecords(history = false) { store, records ->
            val key = records.first().key
            assertIs<ChangeSuccess<UniqueRepairRecord>>(store.execute(UniqueRepairRecord.change(key.change(
                Change(UniqueRepairRecord.ref { label } with "zzzz-original"),
            ))).statuses.single())
            damageCurrentPayload(store, key)
            val before = store.execute(UniqueRepairRecord.get(key)).values.single()
            val response = store.execute(UniqueRepairRecord.change(key.change(
                Change(UniqueRepairRecord.ref { label } with "rejected"),
            )))
            val failure = assertIs<ServerFail<UniqueRepairRecord>>(response.statuses.single())
            assertTrue(assertIs<StorageException>(failure.cause).message.orEmpty().contains("exceeds 512 rows"))
            assertEquals(before, store.execute(UniqueRepairRecord.get(key)).values.single())
            assertIs<ValidationFail<UniqueRepairRecord>>(add(store, "zzzz-original"))
            assertIs<AddSuccess<UniqueRepairRecord>>(add(store, "rejected"))
        }
    }

    private suspend fun withRecords(
        history: Boolean,
        otherClaims: Int = 513,
        block: suspend (FoundationDBDataStore, List<AddSuccess<UniqueRepairRecord>>) -> Unit,
    ) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "unique-ownership-repair", Uuid.random().toString()),
            dataModelsById = mapOf(1u to UniqueRepairRecord),
            keepAllVersions = history,
        )
        try {
            val added = store.bulkAdd(UniqueRepairRecord.add(*Array(otherClaims + 1) { index ->
                UniqueRepairRecord.create { label with "claim-$index" }
            })).statuses.map { assertIs<AddSuccess<UniqueRepairRecord>>(it) }
            block(store, added)
        } finally { store.close() }
    }

    private suspend fun add(store: FoundationDBDataStore, label: String) =
        store.execute(UniqueRepairRecord.add(UniqueRepairRecord.create { this.label with label })).statuses.single()

    private fun restore(key: Key<UniqueRepairRecord>, version: ULong, label: String): RestoreVersionRequest {
        val changes = DataObjectVersionedChange<IsRootDataModel>(Key(key.bytes), changes = listOf(
            VersionedChanges(version, listOf(Change(UniqueRepairRecord.ref { this.label } with label))),
        ))
        return RestoreVersionRequest(listOf(UpdateResponse<IsRootDataModel>(UniqueRepairRecord,
            InitialChangesUpdate(version + 1uL, listOf(changes)),
        )))
    }

    private fun damageCurrentPayload(store: FoundationDBDataStore, key: Key<UniqueRepairRecord>) {
        val dirs = store.getTableDirs(1u)
        val storageKey = packKey(dirs.tablePrefix, key.bytes, UniqueRepairRecord.label.ref().toStorageByteArray())
        store.tc.run { transaction ->
            val stored = requireNotNull(transaction.get(storageKey).awaitResult())
            transaction.set(storageKey, stored.copyOfRange(0, VERSION_BYTE_SIZE) +
                byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + "damaged".encodeToByteArray())
        }
    }
}
