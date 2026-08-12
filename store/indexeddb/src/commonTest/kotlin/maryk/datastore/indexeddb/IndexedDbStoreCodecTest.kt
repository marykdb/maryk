package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.indexeddb.processors.createStoragePlan
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexedDbStoreCodecTest {
    @Test
    fun legacyRecordReadRetriesWhenCommitCrossesMetadataAndRows() = runTest {
        val key = ByteArray(SimpleMarykModel.Meta.keyByteSize) { 1 }
        val oldMeta = encodeRecordMeta(IndexedDbRecordMeta(1u, 1u, false))
        val newMeta = encodeRecordMeta(IndexedDbRecordMeta(2u, 2u, false))
        val row = createStoragePlan(
            dataModel = SimpleMarykModel,
            modelId = 1u,
            keyBytes = key,
            values = SimpleMarykModel.create { value with "ha current" },
            sensitiveFields = IndexedDbSensitiveFieldSupport(mapOf(1u to SimpleMarykModel), null),
        ).tableRows.single()
        val store = InterleavingLegacyRecordStore(oldMeta, newMeta, row)

        val record = store.readRecord(
            dataModel = SimpleMarykModel,
            keyStoreName = "k:1",
            tableStoreName = "t:1",
            keyBytes = key,
            select = null,
        )

        requireNotNull(record)
        assertEquals(2uL, record.lastVersion)
        assertEquals("ha current", record.values[SimpleMarykModel { value::ref }])
        assertEquals(2, store.scanCount)
    }
}

private class InterleavingLegacyRecordStore(
    private val oldMeta: ByteArray,
    private val newMeta: ByteArray,
    private val row: Pair<ByteArray, ByteArray>,
) : IndexedDbByteStore {
    private var metadataReads = 0
    var scanCount = 0
        private set

    override suspend fun get(storeName: String, key: ByteArray): ByteArray? = when (storeName) {
        "k:1" -> if (metadataReads++ == 0) oldMeta else newMeta
        else -> null
    }

    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) = Unit

    override suspend fun delete(storeName: String, key: ByteArray) = Unit

    override suspend fun scan(
        storeName: String,
        startKey: ByteArray?,
        includeStart: Boolean,
        endKey: ByteArray?,
        includeEnd: Boolean,
        reverse: Boolean,
        limit: UInt,
    ): List<Pair<ByteArray, ByteArray>> {
        scanCount++
        return listOf(row)
    }

    override suspend fun close() = Unit
}
