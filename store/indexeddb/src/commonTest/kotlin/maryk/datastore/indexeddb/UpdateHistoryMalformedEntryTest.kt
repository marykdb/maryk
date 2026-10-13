package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.requests.add
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.indexeddb.processors.createUpdateHistoryRowKey
import maryk.test.models.SimpleMarykModel
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertContentEquals

class UpdateHistoryMalformedEntryTest {
    @Test
    fun sensitiveHistoryMigrationSkipsMalformedIdentities() = runTest {
        installIndexedDbForTests()
        val name = "malformed-sensitive-history-${Random.nextInt()}"
        val models = mapOf(1u to SensitiveHistoryModel)
        val provider = IdentityTestEncryptionProvider()
        var store = IndexedDbDataStore.open(name, models, keepAllVersions = true, keepUpdateHistoryIndex = true, fieldEncryptionProvider = provider)
        try {
            val added = assertIs<AddSuccess<SensitiveHistoryModel>>(
                store.execute(SensitiveHistoryModel.add(SensitiveHistoryModel.create { value with "secret" })).statuses.single()
            )
            val malformed = byteArrayOf(2)
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Delete("meta", "sensitive-change-log-v1:1:history".encodeToByteArray()),
                IndexedDbWriteOperation.Put("uh:1", byteArrayOf(0), malformed),
                IndexedDbWriteOperation.Put("uh:1", createUpdateHistoryRowKey(added.version, added.key.bytes) + byteArrayOf(1), malformed),
            ))
            store.close()
            store = IndexedDbDataStore.open(name, models, keepAllVersions = true, keepUpdateHistoryIndex = true, fieldEncryptionProvider = provider)
            assertContentEquals(malformed, store.byteStore.get("uh:1", byteArrayOf(0)))
            assertEquals("secret", store.execute(SensitiveHistoryModel.get(added.key)).values.single().values[SensitiveHistoryModel.ref { value }])
            assertEquals(listOf(added.version), store.execute(SensitiveHistoryModel.scanUpdateHistory()).updates.map { it.version })
        } finally {
            store.close()
        }
    }

    @Test
    fun historyPagingSkipsShortAndLongMalformedEntriesBeforeDecoding() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(
            databaseName = "malformed-history-read-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true, keepUpdateHistoryIndex = true,
        )
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })).statuses.single()
            )
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Put("uh:1", byteArrayOf(0), byteArrayOf(1)),
                IndexedDbWriteOperation.Put("uh:1", createUpdateHistoryRowKey(added.version, added.key.bytes) + byteArrayOf(1), byteArrayOf(1)),
            ))
            for (page in listOf(
                store.execute(SimpleMarykModel.getUpdateHistory(added.key, limit = 1u)),
                store.execute(SimpleMarykModel.scanUpdateHistory(limit = 1u))
            )) {
                assertEquals(listOf(added.version), page.updates.map { it.version })
                assertNull(page.nextCursor)
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun hardDeleteHistoryMigrationSkipsMalformedIdentities() = runTest {
        installIndexedDbForTests()
        val name = "malformed-history-migrate-${Random.nextInt()}"
        val models = mapOf(1u to SimpleMarykModel)
        var store = IndexedDbDataStore.open(name, models, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = assertIs<AddSuccess<SimpleMarykModel>>(
                store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" })).statuses.single()
            )
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Delete("meta", "hard-delete-history:1".encodeToByteArray()),
                IndexedDbWriteOperation.Put("uh:1", byteArrayOf(0), byteArrayOf(1)),
                IndexedDbWriteOperation.Put("uh:1", createUpdateHistoryRowKey(added.version, added.key.bytes) + byteArrayOf(1), byteArrayOf(1)),
            ))
            store.close()
            store = IndexedDbDataStore.open(name, models, keepAllVersions = true, keepUpdateHistoryIndex = true)
            assertEquals(0, store.byteStore.scan("hd:1").size)
            assertEquals(0, store.byteStore.scan("hdk:1").size)
        } finally {
            store.close()
        }
    }
}

private object SensitiveHistoryModel : RootDataModel<SensitiveHistoryModel>() {
    val value by string(1u, sensitive = true)
}

private class IdentityTestEncryptionProvider : ContextualFieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
}
