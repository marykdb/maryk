package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.indexeddb.processors.createChangeLogRowKey
import maryk.datastore.indexeddb.processors.createHistoricSnapshotRowKey
import maryk.datastore.indexeddb.processors.createUpdateHistoryRowKey
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeVersionedChange
import maryk.datastore.indexeddb.processors.readStorageRows
import maryk.datastore.shared.updates.Update
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class StoredHistoryPayloadIntegrityTest {
    @Test
    fun historyRejectsFuturePayloadVersionUnderOlderRowKey() = runTest(timeout = 3.minutes) {
        verifyCorruptPayload { key, version ->
            DataObjectVersionedChange(key = key, changes = listOf(VersionedChanges(version + 1uL, listOf(Change(SimpleMarykModel.ref { value } with "future")))))
        }
    }

    @Test
    fun historyRejectsPayloadForAnotherRecord() = runTest(timeout = 3.minutes) {
        verifyCorruptPayload { _, version ->
            DataObjectVersionedChange(key = SimpleMarykModel.key(SimpleMarykModel.create { value with "other" }),
                changes = listOf(VersionedChanges(version, listOf(Change(SimpleMarykModel.ref { value } with "other")))))
        }
    }

    @Test
    fun historyRejectsMultipleRevisionsUnderOneRowKey() = runTest(timeout = 3.minutes) {
        verifyCorruptPayload { key, version ->
            DataObjectVersionedChange(key = key, changes = listOf(
                VersionedChanges(version, listOf(Change(SimpleMarykModel.ref { value } with "first"))),
                VersionedChanges(version + 1uL, listOf(Change(SimpleMarykModel.ref { value } with "second"))),
            ))
        }
    }

    @Test
    fun boundedChangeQueriesDoNotDecodeExcludedPayloads() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("excluded-history-payload-${Random.nextInt()}",
            mapOf(1u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })
            ).statuses.single())
            val changed = assertIs<ChangeSuccess<SimpleMarykModel>>(store.execute(SimpleMarykModel.change(
                added.key.change(Change(SimpleMarykModel.ref { value } with "haha2"))
            )).statuses.single()).version
            for ((excluded, included) in listOf(added.version to changed, changed to added.version)) {
                val rowKey = createChangeLogRowKey(added.key.bytes, excluded)
                val original = assertNotNull(store.byteStore.get("c:1", rowKey))
                val corrupt = byteArrayOf(0xff.toByte())
                store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("c:1", rowKey, corrupt)))
                for (response in listOf(
                    store.execute(SimpleMarykModel.getChanges(added.key, fromVersion = included, toVersion = included)),
                    store.execute(SimpleMarykModel.scanChanges(fromVersion = included, toVersion = included)),
                )) assertEquals(listOf(included), response.changes.single().changes.map { it.version })
                for (response in listOf(
                    store.execute(SimpleMarykModel.getUpdates(added.key, fromVersion = included, toVersion = included)),
                    store.execute(SimpleMarykModel.scanUpdates(fromVersion = included, toVersion = included)),
                )) assertEquals(listOf(included), response.updates.filterNot { it is OrderedKeysUpdate }.map { it.version })
                assertContentEquals(corrupt, store.byteStore.get("c:1", rowKey))
                store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("c:1", rowKey, original)))
            }
        } finally { store.close() }
    }

    @Test
    fun historicReadsSkipMalformedSnapshotIdentitiesBeforeTheirLimit() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("malformed-snapshot-identity-${Random.nextInt()}",
            mapOf(1u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })
            ).statuses.single())
            val changed = assertIs<ChangeSuccess<SimpleMarykModel>>(store.execute(SimpleMarykModel.change(
                added.key.change(Change(SimpleMarykModel.ref { value } with "haha2"))
            )).statuses.single()).version
            val oldPayload = assertNotNull(store.byteStore.get("ht:1", createHistoricSnapshotRowKey(added.key.bytes, added.version)))
            val upper = changed + 1uL
            val malformed = createHistoricSnapshotRowKey(added.key.bytes, upper) + byteArrayOf(0)
            val malformedKeys = (0..259).map { malformed + byteArrayOf((it shr 8).toByte(), it.toByte()) } +
                listOf(malformed, createHistoricSnapshotRowKey(added.key.bytes, changed).dropLast(1).toByteArray())
            store.byteStore.writeBatch(malformedKeys.map { IndexedDbWriteOperation.Put("ht:1", it, oldPayload) })
            for (response in listOf(
                store.execute(SimpleMarykModel.get(added.key, toVersion = upper)),
                store.execute(SimpleMarykModel.scan(toVersion = upper, limit = 1u, allowTableScan = true)),
            )) {
                val visible = response.values.single()
                assertEquals("haha2", visible.values[SimpleMarykModel.ref { value }])
                assertEquals(changed, visible.lastVersion)
            }
            val referencedRows = assertNotNull(store.readStorageRows(SimpleMarykModel, added.key.bytes, upper))
            assertEquals("haha2", decodeStorageRowsToValues(SimpleMarykModel, referencedRows, null)
                ?.get(SimpleMarykModel.ref { value }))
            for (rowKey in malformedKeys) assertContentEquals(oldPayload, store.byteStore.get("ht:1", rowKey))
            assertEquals("haha", store.execute(SimpleMarykModel.get(added.key, toVersion = added.version))
                .values.single().values[SimpleMarykModel.ref { value }])
            assertEquals(0, store.execute(SimpleMarykModel.get(added.key, toVersion = added.version - 1uL)).values.size)
            assertEquals("haha2", store.execute(SimpleMarykModel.get(added.key))
                .values.single().values[SimpleMarykModel.ref { value }])
        } finally { store.close() }
    }

    @Test
    fun historicSnapshotsRejectMetadataOutsideTheirStoredRevision() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("snapshot-metadata-integrity-${Random.nextInt()}",
            mapOf(1u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })
            ).statuses.single())
            val rowKey = createHistoricSnapshotRowKey(added.key.bytes, added.version)
            val original = assertNotNull(store.byteStore.get("ht:1", rowKey))
            val rows = decodeHistoricSnapshot(original).second
            for (meta in listOf(
                IndexedDbRecordMeta(added.version, added.version + 1uL, false),
                IndexedDbRecordMeta(added.version + 1uL, added.version, false),
                IndexedDbRecordMeta(added.version, added.version - 1uL, false),
            )) {
                val corrupt = encodeHistoricSnapshot(meta, rows)
                store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("ht:1", rowKey, corrupt)))
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.get(added.key, toVersion = added.version)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.scan(toVersion = added.version, allowTableScan = true)) }
                assertFailsWith<StorageException> { store.readStorageRows(SimpleMarykModel, added.key.bytes, added.version) }
                assertContentEquals(corrupt, store.byteStore.get("ht:1", rowKey))
                assertEquals(added.version, store.execute(SimpleMarykModel.get(added.key)).values.single().lastVersion)
            }
            store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("ht:1", rowKey, original)))
            assertEquals(added.version, store.execute(SimpleMarykModel.get(added.key, toVersion = added.version)).values.single().lastVersion)
        } finally { store.close() }
    }

    @Test
    fun commitJournalRejectsMalformedIdentityAndFutureEvent() = runTest(timeout = 3.minutes) {
        val key = SimpleMarykModel.key(SimpleMarykModel.create { value with "haha" })
        val version = 123uL
        val fields = IndexedDbSensitiveFieldSupport(mapOf(1u to SimpleMarykModel), null)
        val update = Update.Deletion(SimpleMarykModel, key, version, false)
        val encoded = encodeCommitJournalEntry(1u, update, null)
        val canonical = createCommitJournalKey(version, 0u)
        for (validKey in listOf(canonical, createCommitJournalKey(version + 1uL, 0u))) {
            val decoded = assertIs<Update.Deletion<SimpleMarykModel>>(
                decodeCommitJournalEntry(mapOf(1u to SimpleMarykModel), fields, validKey, encoded)
            )
            assertEquals(key, decoded.key)
            assertEquals(version, decoded.version)
            assertEquals(false, decoded.isHardDelete)
        }
        for (rowKey in listOf(
            createCommitJournalKey(version - 1uL, 0u),
            canonical.copyOf(ULong.SIZE_BYTES),
            canonical + byteArrayOf(0),
        )) {
            assertFailsWith<StorageException> {
                decodeCommitJournalEntry(mapOf(1u to SimpleMarykModel), fields, rowKey, encoded)
            }
        }
        assertFailsWith<StorageException> {
            decodeCommitJournalEntry(mapOf(1u to SimpleMarykModel), fields, canonical, encoded.dropLast(1).toByteArray())
        }
    }

    private suspend fun verifyCorruptPayload(
        payload: (Key<SimpleMarykModel>, ULong) -> DataObjectVersionedChange<SimpleMarykModel>,
    ) {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("stored-history-integrity-${Random.nextInt()}",
            mapOf(1u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(store.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })
            ).statuses.single())
            val historyKey = createUpdateHistoryRowKey(added.version, added.key.bytes)
            val changeKey = createChangeLogRowKey(added.key.bytes, added.version)
            val originalHistory = assertNotNull(store.byteStore.get("uh:1", historyKey))
            val originalChange = assertNotNull(store.byteStore.get("c:1", changeKey))
            val corrupt = encodeVersionedChange(SimpleMarykModel, payload(added.key, added.version))
            val fields = IndexedDbSensitiveFieldSupport(mapOf(1u to SimpleMarykModel), null)
            val journalPayload = encodeChangeJournalPayload(SimpleMarykModel, 1u, fields,
                added.key.bytes, added.version, corrupt, emptyList())
            val journalEntry = encodeCommitJournalEntry(1u,
                Update.Change(SimpleMarykModel, added.key, added.version, emptyList()), journalPayload)
            assertFailsWith<StorageException> {
                decodeCommitJournalEntry(mapOf(1u to SimpleMarykModel), fields,
                    createCommitJournalKey(added.version, 0u), journalEntry)
            }
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Put("uh:1", historyKey, corrupt),
                IndexedDbWriteOperation.Put("c:1", changeKey, corrupt),
            ))
            for (upper in listOf(null, added.version)) {
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.getUpdateHistory(added.key, toVersion = upper)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.scanUpdateHistory(toVersion = upper)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.getChanges(added.key, toVersion = upper)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.scanChanges(toVersion = upper)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.getUpdates(added.key, toVersion = upper)) }
                assertFailsWith<StorageException> { store.execute(SimpleMarykModel.scanUpdates(toVersion = upper)) }
            }
            assertContentEquals(corrupt, store.byteStore.get("uh:1", historyKey))
            assertContentEquals(corrupt, store.byteStore.get("c:1", changeKey))
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Put("uh:1", historyKey, originalHistory),
                IndexedDbWriteOperation.Put("c:1", changeKey, originalChange),
            ))
            assertEquals(listOf(added.version), store.execute(SimpleMarykModel.getUpdateHistory(added.key)).updates.map { it.version })
        } finally { store.close() }
    }
}
