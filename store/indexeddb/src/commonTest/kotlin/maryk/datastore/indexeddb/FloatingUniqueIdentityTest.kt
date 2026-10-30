package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.datastore.indexeddb.processors.createUniqueRowKey
import maryk.datastore.shared.TypeIndicator
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object RawUniqueRecord : RootDataModel<RawUniqueRecord>() {
    val double by number(1u, type = Float64, unique = true, required = false)
    val float by number(2u, type = Float32, unique = true, required = false)
}

private data class EncodedNaNRow(
    val tableKey: ByteArray,
    val encoded: ByteArray,
    val uniqueKey: ByteArray,
    val owner: ByteArray,
)

class FloatingUniqueIdentityTest {
    @Test
    fun float64UniqueClaimsTransferBetweenRepresentablePatterns() = runTest {
        exerciseLifecycle(
            if (supportsDistinctNaNPayloadsForTests()) {
                listOf(0x7ff8000000000001L, 0x7ff8000000000002L, 0x7ff8000000000003L).map(Double::fromBits)
            } else listOf(0.0, -0.0, 1.0),
            create = { value -> RawUniqueRecord.create { double with value } },
            change = { value -> Change(RawUniqueRecord.ref { double } with value) },
            filter = { value -> Equals(RawUniqueRecord.ref { double } with value) },
            read = { values -> values { double } },
            bits = Double::toRawBits,
        )
    }

    @Test
    fun float32UniqueClaimsTransferBetweenRepresentablePatterns() = runTest {
        exerciseLifecycle(
            if (supportsDistinctNaNPayloadsForTests()) {
                listOf(0x7fc00001, 0x7fc00002, 0x7fc00003).map(Float::fromBits)
            } else listOf(0.0f, -0.0f, 1.0f),
            create = { value -> RawUniqueRecord.create { float with value } },
            change = { value -> Change(RawUniqueRecord.ref { float } with value) },
            filter = { value -> Equals(RawUniqueRecord.ref { float } with value) },
            read = { values -> values { float } },
            bits = { it.toRawBits().toLong() },
        )
    }

    @Test
    fun encodedNaNCellsAndUniqueKeysRemainByteExactAcrossReopen() = runTest {
        installIndexedDbForTests()
        val database = "maryk-encoded-raw-unique-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to RawUniqueRecord))
        var store = open()
        // Literal rows exercise storage and claim persistence without passing through JS Numbers.
        val rows = mutableListOf<EncodedNaNRow>()
        try {
            for (width in listOf(4, 8)) for (payload in 1L..3L) {
                val bits = if (width == 8) 0x7ff8000000000000L + payload else 0x7fc00000L + payload
                val ordered = bits xor if (width == 8) Long.MIN_VALUE else 0x80000000L
                val encoded = ByteArray(width + 1).also { bytes ->
                    bytes[0] = TypeIndicator.NoTypeIndicator.byte
                    repeat(width) { offset -> bytes[offset + 1] = (ordered ushr ((width - offset - 1) * 8)).toByte() }
                }
                val reference = if (width == 8) RawUniqueRecord.ref { double }.toStorageByteArray()
                    else RawUniqueRecord.ref { float }.toStorageByteArray()
                val key = ByteArray(RawUniqueRecord.Meta.keyByteSize) { (width + payload.toInt()).toByte() }
                val tableKey = createTableRowKey(key, reference)
                val uniqueKey = createUniqueRowKey(reference, encoded)
                store.byteStore.writeBatch(listOf(
                    IndexedDbWriteOperation.Put("t:1", tableKey, encoded),
                    IndexedDbWriteOperation.Put("u:1", uniqueKey, key),
                ))
                rows += EncodedNaNRow(tableKey, encoded, uniqueKey, key)
            }
            store.close()
            store = open()
            assertEquals(6, store.byteStore.scan("u:1").size)
            for (row in rows) {
                assertContentEquals(row.encoded, store.byteStore.get("t:1", row.tableKey))
                assertContentEquals(row.owner, store.byteStore.get("u:1", row.uniqueKey))
            }
        } finally { store.close() }
    }

    private suspend fun <T : Any> exerciseLifecycle(
        numbers: List<T>,
        create: (T) -> Values<RawUniqueRecord>,
        change: (T?) -> Change,
        filter: (T) -> Equals,
        read: (Values<RawUniqueRecord>) -> T?,
        bits: (T) -> Long,
    ) {
        installIndexedDbForTests()
        for (keepHistory in listOf(false, true)) {
            val database = "maryk-raw-unique-$keepHistory-${Random.nextInt()}"
            suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to RawUniqueRecord), keepAllVersions = keepHistory)
            var store = open()
            suspend fun lookup(value: T) = store.execute(RawUniqueRecord.scan(where = filter(value))).also {
                assertIs<FetchByUniqueKey>(it.dataFetchType)
            }.values.map { it.key }
            try {
                val first = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                val other = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[2]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                val changed = assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(
                    first.key.change(change(numbers[1])),
                )).statuses.single())
                assertTrue(changed.version > first.version)
                assertEquals(bits(numbers[1]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
                assertEquals(emptyList(), lookup(numbers[0]))
                assertEquals(listOf(first.key), lookup(numbers[1]))
                assertEquals(listOf(other.key), lookup(numbers[2]))
                assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                val noOp = assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(
                    first.key.change(change(numbers[1])),
                )).statuses.single())
                assertEquals(changed.version, noOp.version)
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(
                    first.key.change(change(numbers[2])),
                )).statuses.single())

                store.close()
                store = open()
                assertEquals(bits(numbers[1]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
                assertEquals(listOf(first.key), lookup(numbers[1]))
                if (keepHistory) {
                    val old = store.execute(RawUniqueRecord.scan(where = filter(numbers[0]), toVersion = first.version)).values.single()
                    assertEquals(first.key, old.key)
                    assertEquals(bits(numbers[0]), bits(assertNotNull(read(old.values))))
                    val updated = store.execute(RawUniqueRecord.scan(where = filter(numbers[1]), toVersion = changed.version)).values.single()
                    assertEquals(first.key, updated.key)
                    assertEquals(bits(numbers[1]), bits(assertNotNull(read(updated.values))))
                }

                assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(null)))).statuses.single())
                val owner = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[1]))).statuses.single())
                assertIs<DeleteSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.delete(owner.key)).statuses.single())
                val replacement = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[1]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(owner.key.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertEquals(listOf(replacement.key), lookup(numbers[1]))
                assertIs<DeleteSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.delete(replacement.key, hardDelete = true)).statuses.single())
                assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(owner.key.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertEquals(listOf(owner.key), lookup(numbers[1]))
                store.close()
                store = open()
                assertEquals(listOf(owner.key), lookup(numbers[1]))
                assertEquals(bits(numbers[1]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(owner.key)).values.single().values))))
            } finally { store.close() }
        }
    }
}
