package maryk.datastore.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

private object ChangeBudgetRecord : RootDataModel<ChangeBudgetRecord>(minimumKeyScanByteRange = 0u) {
    val label by string(1u)
    val payload by string(2u)
}

private object ChangeBudgetCollectionRecord : RootDataModel<ChangeBudgetCollectionRecord>(minimumKeyScanByteRange = 0u) {
    val label by string(1u)
    val payload by list(2u, valueDefinition = StringDefinition())
}

class FoundationDBChangeReadBudgetTest {
    @Test fun currentAndHistoricChangesRespectRecordBudgets() = runBlocking<Unit> {
        val path = listOf("maryk", "change-record-budget", Uuid.random().toString())
        val writer = open(path)
        val added = try {
            assertIs<AddSuccess<ChangeBudgetRecord>>(writer.execute(ChangeBudgetRecord.add(
                ChangeBudgetRecord.create { label with "small"; payload with "x".repeat(2048) },
            )).statuses.single())
        } finally { writer.close() }
        val reader = open(path, 512, 8192)
        try {
            for (historic in listOf(false, true)) {
                val version = added.version.takeIf { historic }
                assertFailsWith<FoundationDBReadLimitExceededException> {
                    reader.execute(ChangeBudgetRecord.getChanges(added.key, toVersion = version))
                }
                assertFailsWith<FoundationDBReadLimitExceededException> {
                    reader.execute(ChangeBudgetRecord.scanChanges(toVersion = version))
                }
                assertFailsWith<FoundationDBReadLimitExceededException> {
                    reader.execute(ChangeBudgetRecord.getUpdates(added.key, toVersion = version))
                }
                assertFailsWith<FoundationDBReadLimitExceededException> {
                    reader.execute(ChangeBudgetRecord.scanUpdates(toVersion = version))
                }
                assertFailsWith<FoundationDBReadLimitExceededException> {
                    reader.execute(ChangeBudgetRecord.scanUpdateHistory(toVersion = version))
                }
            }
        } finally { reader.close() }
    }

    @Test fun requestsShareTheBudgetAcrossRecords() = runBlocking<Unit> {
        val path = listOf("maryk", "change-request-budget", Uuid.random().toString())
        val writer = open(path)
        val keys = try {
            writer.execute(ChangeBudgetRecord.add(*Array(3) {
                ChangeBudgetRecord.create { label with "small"; payload with "x".repeat(1800) }
            })).statuses.map { assertIs<AddSuccess<ChangeBudgetRecord>>(it).key }
        } finally { writer.close() }
        val reader = open(path, 4096, 5000)
        try {
            assertFailsWith<FoundationDBReadLimitExceededException> {
                reader.execute(ChangeBudgetRecord.getChanges(*keys.toTypedArray()))
            }
            assertFailsWith<FoundationDBReadLimitExceededException> { reader.execute(ChangeBudgetRecord.scanChanges()) }
            assertFailsWith<FoundationDBReadLimitExceededException> {
                reader.execute(ChangeBudgetRecord.getUpdates(*keys.toTypedArray()))
            }
            assertFailsWith<FoundationDBReadLimitExceededException> { reader.execute(ChangeBudgetRecord.scanUpdates()) }
            assertFailsWith<FoundationDBReadLimitExceededException> { reader.execute(ChangeBudgetRecord.scanUpdateHistory()) }
        } finally { reader.close() }
    }

    @Test fun versionBoundsAndProjectionRemainCorrectAndRawHistoryIsCharged() = runBlocking<Unit> {
        val path = listOf("maryk", "change-history-budget", Uuid.random().toString())
        val writer = open(path)
        val added = assertIs<AddSuccess<ChangeBudgetRecord>>(writer.execute(ChangeBudgetRecord.add(
            ChangeBudgetRecord.create { label with "small"; payload with "a".repeat(600) },
        )).statuses.single())
        val middle = assertIs<ChangeSuccess<ChangeBudgetRecord>>(writer.execute(ChangeBudgetRecord.change(
            added.key.change(Change(ChangeBudgetRecord.ref { payload } with "b".repeat(600))),
        )).statuses.single()).version
        val last = assertIs<ChangeSuccess<ChangeBudgetRecord>>(writer.execute(ChangeBudgetRecord.change(
            added.key.change(Change(ChangeBudgetRecord.ref { payload } with "c".repeat(600))),
        )).statuses.single()).version
        try {
            val result = writer.execute(ChangeBudgetRecord.getChanges(added.key,
                select = ChangeBudgetRecord.graph { listOf(payload) },
                fromVersion = middle, toVersion = last, maxVersions = 2u,
            )).changes.single()
            assertEquals(listOf(middle, last), result.changes.map { it.version })
            assertEquals(listOf(middle), writer.execute(ChangeBudgetRecord.getChanges(added.key,
                fromVersion = middle, toVersion = middle, maxVersions = 2u,
            )).changes.single().changes.map { it.version })
            assertEquals(0, writer.execute(ChangeBudgetRecord.getChanges(added.key,
                select = ChangeBudgetRecord.graph { listOf(label) }, fromVersion = middle,
                toVersion = last, maxVersions = 2u,
            )).changes.size)
        } finally { writer.close() }
        val reader = open(path, 1024, 8192)
        try {
            // A prepared scalar projection does not fetch the unselected payload history.
            assertEquals(0, reader.execute(ChangeBudgetRecord.getChanges(added.key,
                select = ChangeBudgetRecord.graph { listOf(label) }, fromVersion = middle,
                toVersion = last, maxVersions = 1u,
            )).changes.size)
            // Both selected payload revisions must still be charged to this record.
            assertFailsWith<FoundationDBReadLimitExceededException> {
                reader.execute(ChangeBudgetRecord.getChanges(added.key,
                    select = ChangeBudgetRecord.graph { listOf(payload) }, fromVersion = middle,
                    toVersion = last, maxVersions = 2u,
                ))
            }
        } finally { reader.close() }
    }

    @Test fun reconstructedCollectionsShareTheLogicalRecordBudget() = runBlocking<Unit> {
        val path = listOf("maryk", "change-collection-budget", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(directoryPath = path,
            dataModelsById = mapOf(1u to ChangeBudgetCollectionRecord), keepAllVersions = true,
        )
        val added = try {
            assertIs<AddSuccess<ChangeBudgetCollectionRecord>>(writer.execute(ChangeBudgetCollectionRecord.add(
                ChangeBudgetCollectionRecord.create { label with "small"; payload with listOf("x".repeat(512)) },
            )).statuses.single())
        } finally { writer.close() }
        val reader = FoundationDBDataStore.open(directoryPath = path,
            dataModelsById = mapOf(1u to ChangeBudgetCollectionRecord), keepAllVersions = true,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(maxRecordReadBytes = 1024, maxRequestReadBytes = 8192),
        )
        try {
            // The raw record fits; including a reconstruction of its collection does not.
            assertEquals(1, reader.execute(ChangeBudgetCollectionRecord.getChanges(added.key,
                select = ChangeBudgetCollectionRecord.graph { listOf(label) },
            )).changes.size)
            val failure = assertFailsWith<FoundationDBReadLimitExceededException> {
                reader.execute(ChangeBudgetCollectionRecord.getChanges(added.key))
            }
            assertEquals(1024L, failure.limitBytes)
        } finally { reader.close() }
    }

    @Test fun encryptedScanChangesRetainCurrentAndHistoricValues() = runBlocking<Unit> {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "change-encryption", Uuid.random().toString()),
            dataModelsById = mapOf(901u to SensitiveRecord), keepAllVersions = true,
            fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { 5 }, ByteArray(32) { 6 }),
        )
        try {
            val added = assertIs<AddSuccess<SensitiveRecord>>(store.execute(SensitiveRecord.add(
                SensitiveRecord(Bytes(ByteArray(16) { 1 }), "record", "before"),
            )).statuses.single())
            store.execute(SensitiveRecord.change(added.key.change(Change(SensitiveRecord.ref { secret } with "after"))))
            for (version in listOf(null, added.version)) {
                val get = store.execute(SensitiveRecord.getChanges(added.key, toVersion = version)).changes
                val scan = store.execute(SensitiveRecord.scanChanges(toVersion = version)).changes
                assertEquals(get, scan)
                // Update requests share the same change decoder, including historic scans.
                store.execute(SensitiveRecord.scanUpdates(toVersion = version))
            }
        } finally { store.close() }
    }

    private suspend fun open(path: List<String>, record: Long = 16 * 1024 * 1024, request: Long = 32 * 1024 * 1024) =
        FoundationDBDataStore.open(directoryPath = path, dataModelsById = mapOf(1u to ChangeBudgetRecord),
            keepAllVersions = true, keepUpdateHistoryIndex = true,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(maxRecordReadBytes = record, maxRequestReadBytes = request),
        )
}
