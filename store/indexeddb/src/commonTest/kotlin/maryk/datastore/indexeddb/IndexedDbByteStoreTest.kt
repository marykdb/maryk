package maryk.datastore.indexeddb

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexedDbByteStoreTest {
    @Test
    fun roundTripsValuesAndScansInOrder() = runTest {
        installIndexedDbForTests()

        val store = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-byte-store-test",
            objectStoreNames = setOf("scan")
        )

        try {
            store.put("scan", byteArrayOf(1), byteArrayOf(10))
            store.put("scan", byteArrayOf(2), byteArrayOf(20))
            store.put("scan", byteArrayOf(3), byteArrayOf(30))

            assertContentEquals(byteArrayOf(20), store.get("scan", byteArrayOf(2)))

            val ascending = store.scan("scan")
            assertEquals(listOf(1, 2, 3), ascending.map { (key, _) -> key.single().toInt() })

            val descending = store.scan("scan", reverse = true)
            assertEquals(listOf(3, 2, 1), descending.map { (key, _) -> key.single().toInt() })

            val bounded = store.scan(
                "scan",
                startKey = byteArrayOf(2),
                includeStart = false,
                endKey = byteArrayOf(3),
                includeEnd = true
            )
            assertEquals(listOf(3), bounded.map { (key, _) -> key.single().toInt() })

            store.delete("scan", byteArrayOf(2))
            assertNull(store.get("scan", byteArrayOf(2)))
        } finally {
            store.close()
        }
    }

    @Test
    fun scansUnsignedByteKeysAndEmptyRanges() = runTest {
        installIndexedDbForTests()

        val store = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-byte-order-test",
            objectStoreNames = setOf("scan")
        )

        try {
            val keys = listOf(
                byteArrayOf(0x00),
                byteArrayOf(0x7f),
                byteArrayOf(0x80.toByte()),
                byteArrayOf(0xff.toByte()),
            )
            keys.forEachIndexed { index, key ->
                store.put("scan", key, byteArrayOf(index.toByte()))
            }

            assertEquals(
                listOf(0x00, 0x7f, 0x80, 0xff),
                store.scan("scan").map { (key, _) -> key.single().toInt() and 0xff }
            )
            assertEquals(
                listOf(0xff, 0x80, 0x7f, 0x00),
                store.scan("scan", reverse = true).map { (key, _) -> key.single().toInt() and 0xff }
            )

            assertTrue {
                store.scan(
                    "scan",
                    startKey = byteArrayOf(0x80.toByte()),
                    endKey = byteArrayOf(0x80.toByte()),
                    includeStart = false,
                    includeEnd = true,
                ).isEmpty()
            }
            assertTrue {
                store.scan(
                    "scan",
                    startKey = byteArrayOf(0xff.toByte()),
                    endKey = byteArrayOf(0x7f),
                ).isEmpty()
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun objectScopedPrefixesDoNotCollide() = runTest {
        installIndexedDbForTests()

        val store = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-object-prefix-test",
            objectStoreNames = setOf("rows")
        )

        val key = byteArrayOf(1)
        val keyWithSameRawPrefix = byteArrayOf(1, 2)
        val prefix = createObjectRowKeyPrefix(key)
        val siblingPrefix = createObjectRowKeyPrefix(keyWithSameRawPrefix)

        try {
            store.put("rows", combineToByteArray(prefix, byteArrayOf(1)), byteArrayOf(10))
            store.put("rows", combineToByteArray(siblingPrefix, byteArrayOf(1)), byteArrayOf(20))

            val rows = store.scan(
                storeName = "rows",
                startKey = prefix,
                endKey = prefix.nextPrefixUpperBound(),
                includeEnd = false,
            ).filter { (rowKey, _) ->
                rowKey.matchesRangePart(0, prefix, sourceLength = rowKey.size, length = prefix.size)
            }

            assertEquals(1, rows.size)
            assertContentEquals(byteArrayOf(10), rows.single().second)
        } finally {
            store.close()
        }
    }

    @Test
    fun writeBatchFailsWithoutPartialWrites() = runTest {
        installIndexedDbForTests()

        val store = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-byte-batch-atomic-test",
            objectStoreNames = setOf("rows")
        )

        try {
            store.writeBatch(
                listOf(
                    IndexedDbWriteOperation.Put("rows", byteArrayOf(1), byteArrayOf(10)),
                    IndexedDbWriteOperation.Put("rows", byteArrayOf(2), byteArrayOf(20)),
                )
            )

            assertContentEquals(byteArrayOf(10), store.get("rows", byteArrayOf(1)))
            assertContentEquals(byteArrayOf(20), store.get("rows", byteArrayOf(2)))

            assertFailsWith<Throwable> {
                store.writeBatch(
                    listOf(
                        IndexedDbWriteOperation.Put("rows", byteArrayOf(3), byteArrayOf(30)),
                        IndexedDbWriteOperation.Put("missing", byteArrayOf(4), byteArrayOf(40)),
                    )
                )
            }

            assertNull(store.get("rows", byteArrayOf(3)))
            assertContentEquals(byteArrayOf(10), store.get("rows", byteArrayOf(1)))
            assertContentEquals(byteArrayOf(20), store.get("rows", byteArrayOf(2)))
        } finally {
            store.close()
        }
    }

    @Test
    fun opensExistingDatabaseWithAdditionalObjectStores() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-byte-schema-upgrade-test-${Random.nextInt()}"
        val initial = openIndexedDbByteStore(
            databaseName = databaseName,
            objectStoreNames = setOf("first")
        )

        try {
            initial.put("first", byteArrayOf(1), byteArrayOf(10))
        } finally {
            initial.close()
        }

        val upgraded = openIndexedDbByteStore(
            databaseName = databaseName,
            objectStoreNames = setOf("first", "second")
        )

        try {
            assertContentEquals(byteArrayOf(10), upgraded.get("first", byteArrayOf(1)))
            upgraded.put("second", byteArrayOf(2), byteArrayOf(20))
            assertContentEquals(byteArrayOf(20), upgraded.get("second", byteArrayOf(2)))
        } finally {
            upgraded.close()
        }

        val reopened = openIndexedDbByteStore(
            databaseName = databaseName,
            objectStoreNames = setOf("first", "second")
        )

        try {
            assertContentEquals(byteArrayOf(20), reopened.get("second", byteArrayOf(2)))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun writeTransactionsSerializeAcrossStoreInstances() = runTest {
        installIndexedDbForTests()

        val first = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-byte-write-lock-test",
            objectStoreNames = setOf("rows")
        )
        val second = openIndexedDbByteStore(
            databaseName = "maryk-indexeddb-byte-write-lock-test",
            objectStoreNames = setOf("rows")
        )
        val events = mutableListOf<String>()

        try {
            val firstJob = async {
                first.transaction(setOf("rows"), IndexedDbTransactionMode.READWRITE) {
                    events += "first-start"
                    delay(50)
                    events += "first-end"
                }
            }
            val secondJob = async {
                while ("first-start" !in events) {
                    delay(1)
                }
                second.transaction(setOf("rows"), IndexedDbTransactionMode.READWRITE) {
                    events += "second"
                }
            }

            firstJob.await()
            secondJob.await()

            assertEquals(listOf("first-start", "first-end", "second"), events)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun scanInBatchesCapsNativePageSize() = runTest {
        val store = RecordingScanByteStore()

        store.scanInBatches(
            storeName = "rows",
            targetLimit = UInt.MAX_VALUE,
        ) { _, _ -> false }

        assertEquals(listOf(256u), store.requestedLimits)
    }

    @Test
    fun scanInBatchesStopsAtTargetLimit() = runTest {
        val store = RecordingScanByteStore(rowsPerScan = 2)
        var processed = 0

        store.scanInBatches(
            storeName = "rows",
            targetLimit = 2u,
        ) { _, _ ->
            processed++
            true
        }

        assertEquals(2, processed)
        assertEquals(listOf(2u), store.requestedLimits)
    }
}

private class RecordingScanByteStore(
    private val rowsPerScan: Int = 1,
) : IndexedDbByteStore {
    val requestedLimits = mutableListOf<UInt>()

    override suspend fun get(storeName: String, key: ByteArray) = null

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
        requestedLimits += limit
        return List(rowsPerScan) { index -> byteArrayOf(index.toByte()) to byteArrayOf(index.toByte()) }
    }

    override suspend fun close() = Unit
}

private fun ByteArray.nextPrefixUpperBound(): ByteArray? {
    val next = copyOf()
    for (index in next.lastIndex downTo 0) {
        if (next[index] != 0xFF.toByte()) {
            next[index]++
            return next
        }
    }
    return null
}
