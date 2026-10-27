package maryk.datastore.test

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ValueDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.types.ValueDataObject
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.GreaterThan
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.values.Values
import maryk.datastore.test.StorageUpgradeRecord.double
import maryk.datastore.test.StorageUpgradeRecord.float
import maryk.datastore.test.StorageUpgradeRecord.text
import maryk.datastore.test.StorageUpgradeRecord.tail
import maryk.datastore.test.StorageUpgradeRecord.uint
import maryk.datastore.test.StorageUpgradeRecord.packed
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

object StorageUpgradeRecord : RootDataModel<StorageUpgradeRecord>(indexes = {
    listOf(double.ref(), float.ref(), Multiple(text.ref(), tail.ref()), Reversed(text.ref()), packed.ref(), uint.ref())
}) {
    val double by number(1u, type = Float64, unique = true)
    val float by number(2u, type = Float32)
    val text by string(3u)
    val tail by string(4u)
    val uint by number(5u, type = UInt32)
    val doubles by set(6u, valueDefinition = NumberDefinition(type = Float64))
    val mapping by map(7u, keyDefinition = NumberDefinition(type = Float32), valueDefinition = NumberDefinition(type = Float64))
    val floats by list(8u, valueDefinition = NumberDefinition(type = Float32))
    val packed by valueObject(9u, dataModel = FloatingStorageValue, unique = true)
}

data class FloatingStorageValue(val double: Double, val float: Float) : ValueDataObject(toBytes(double, float)) {
    companion object : ValueDataModel<FloatingStorageValue, Companion>(FloatingStorageValue::class) {
        val double by number(1u, FloatingStorageValue::double, type = Float64)
        val float by number(2u, FloatingStorageValue::float, type = Float32)
        override fun invoke(values: ObjectValues<FloatingStorageValue, Companion>) = FloatingStorageValue(values(1u), values(2u))
    }
}

/** Raw legacy fixtures use the stable value codec, independently of the new index codec. */
class StorageUpgradeFixture {
    val numbers = listOf(-2.0, -1.0, -0.0, 0.0, 1.0, 2.0, 3.0)
    val texts = listOf("", "a", "az", "b", "b\u0000", "😀", "！")
    val records = numbers.mapIndexed { index, number -> StorageUpgradeRecord.create {
        double with number
        float with number.toFloat()
        text with texts[index]
        tail with if (index == 1) "zz" else "a"
        uint with index.toUInt()
        StorageUpgradeRecord.doubles with setOf(-2.0, 1.0)
        StorageUpgradeRecord.mapping with mapOf(-2f to 1.0, 1f to -2.0)
        StorageUpgradeRecord.floats with listOf(-2f, 1f)
        StorageUpgradeRecord.packed with FloatingStorageValue(number, number.toFloat())
    } }
    lateinit var keys: List<Key<StorageUpgradeRecord>>
    var version = 0uL

    suspend fun addTo(store: IsDataStore) {
        val statuses = store.execute(StorageUpgradeRecord.add(*records.toTypedArray())).statuses
        keys = statuses.map { assertIs<AddSuccess<StorageUpgradeRecord>>(it).key }
        version = assertIs<AddSuccess<StorageUpgradeRecord>>(statuses.last()).version
    }

    fun legacyEntry(index: IsIndexable, record: Values<StorageUpgradeRecord>, key: ByteArray): ByteArray {
        val parts = if (index is Multiple) index.references else listOf(index)
        val components = parts.map { part ->
            val bytes = part.toStorageByteArrays(record).single()
            when (part) {
                StorageUpgradeRecord.double.ref() -> legacyFloatBytes(record { double }!!)
                StorageUpgradeRecord.float.ref() -> legacyFloatBytes(record { float }!!.toDouble(), single = true)
                StorageUpgradeRecord.packed.ref() -> legacyPacked(record { StorageUpgradeRecord.packed }!!)
                else -> bytes
            }
        }
        val lengths = ByteArray(components.sumOf { it.size.calculateVarByteLength() })
        var offset = 0
        components.asReversed().forEach { it.size.writeVarBytes { byte -> lengths[offset++] = byte } }
        return components.fold(byteArrayOf()) { bytes, component -> bytes + component } + lengths + key
    }

    /** Convert native current-format fixture cells back to independently encoded legacy bytes. */
    fun legacyCell(qualifier: ByteArray, value: ByteArray): Pair<ByteArray, ByteArray> {
        if (qualifier.isEmpty() || qualifier[0] == 0.toByte()) return qualifier to value
        var offset = 0
        val property = initUIntByVarWithExtraInfo({ qualifier[offset++] }) { index, _ -> index }
        val target = qualifier.copyOf()
        fun convert(source: ByteArray, single: Boolean): ByteArray {
            var index = 0
            val number = if (single) NumberDefinition(type = Float32).readStorageBytes(source.size) { source[index++] }.toDouble()
                else NumberDefinition(type = Float64).readStorageBytes(source.size) { source[index++] }
            return legacyFloatBytes(number, single)
        }
        if (qualifier.size > offset && property in 6u..7u) {
            val size = initIntByVar { qualifier[offset++] }
            convert(qualifier.copyOfRange(offset, offset + size), property == 7u).copyInto(target, offset)
        }
        if (property == 9u && value.firstOrNull() == 1.toByte()) {
            var position = 1
            val packed = StorageUpgradeRecord.packed.definition.readStorageBytes(value.size - 1) { value[position++] }
            return target to (byteArrayOf(1) + legacyPacked(packed))
        }
        val scalar = property in 1u..2u || qualifier.size > offset && property in 6u..8u
        val convertedValue = if (scalar && value.firstOrNull() == 1.toByte())
            byteArrayOf(1) + convert(value.copyOfRange(1, value.size), property == 2u || property == 8u) else value
        return target to convertedValue
    }

    fun legacyUnique(bytes: ByteArray): ByteArray = if (bytes.size == 1) bytes else {
        val (qualifier, value) = legacyCell(bytes.copyOfRange(0, 1), bytes.copyOfRange(1, bytes.size))
        qualifier + value
    }

    private fun legacyPacked(value: FloatingStorageValue) = legacyFloatBytes(value.double) + byteArrayOf(1) + legacyFloatBytes(value.float.toDouble(), true)

    fun legacyChangeLog(bytes: ByteArray): ByteArray {
        val result = bytes.copyOf()
        for (record in records) {
            val value = record { StorageUpgradeRecord.packed }!!
            for (offset in 0..bytes.size - value.toByteArray().size) {
                if (value.toByteArray().indices.all { bytes[offset + it] == value.toByteArray()[it] }) legacyPacked(value).copyInto(result, offset)
            }
        }
        return result
    }

    suspend fun verify(store: IsDataStore) {
        val all = store.execute(StorageUpgradeRecord.get(*keys.toTypedArray()))
        assertEquals(records, all.values.map { it.values })
        for (atVersion in listOf(null, version)) {
            val numeric = store.execute(StorageUpgradeRecord.scan(
                order = StorageUpgradeRecord.ref { double }.ascending(), toVersion = atVersion,
                where = GreaterThan(StorageUpgradeRecord.ref { double } with -1.0),
            ))
            assertIs<FetchByIndexScan>(numeric.dataFetchType)
            assertEquals(numbers.filter { it > -1.0 }, numeric.values.map { it.values { double } })
            val floats = store.execute(StorageUpgradeRecord.scan(order = StorageUpgradeRecord.ref { float }.ascending(), toVersion = atVersion))
            assertIs<FetchByIndexScan>(floats.dataFetchType)
            assertEquals(numbers.map { it.toFloat() }, floats.values.map { it.values { float } })
            val strings = store.execute(StorageUpgradeRecord.scan(order = Orders(
                StorageUpgradeRecord.ref { text }.ascending(), StorageUpgradeRecord.ref { tail }.ascending(),
            ), toVersion = atVersion))
            assertIs<FetchByIndexScan>(strings.dataFetchType)
            assertEquals(texts.sorted(), strings.values.map { it.values { text } })
            val range = store.execute(StorageUpgradeRecord.scan(order = Orders(
                StorageUpgradeRecord.ref { text }.ascending(), StorageUpgradeRecord.ref { tail }.ascending(),
            ), where = LessThan(StorageUpgradeRecord.ref { text } with "b\u0000"), toVersion = atVersion))
            assertIs<FetchByIndexScan>(range.dataFetchType)
            assertEquals(texts.filter { it < "b\u0000" }.sorted(), range.values.map { it.values { text } })
            val prefix = store.execute(StorageUpgradeRecord.scan(order = Orders(
                StorageUpgradeRecord.ref { text }.ascending(), StorageUpgradeRecord.ref { tail }.ascending(),
            ), where = Prefix(StorageUpgradeRecord.ref { text } with "a"), toVersion = atVersion))
            assertIs<FetchByIndexScan>(prefix.dataFetchType)
            assertEquals(listOf("a", "az"), prefix.values.map { it.values { text } })
            val reversed = store.execute(StorageUpgradeRecord.scan(order = StorageUpgradeRecord.ref { text }.descending(), toVersion = atVersion))
            assertIs<FetchByIndexScan>(reversed.dataFetchType)
            assertEquals(texts.sortedDescending(), reversed.values.map { it.values { text } })
        }
    }
}

/** Original transform plus signed integer byte encoding, deliberately independent of production migration. */
fun legacyFloatBytes(value: Double, single: Boolean = false): ByteArray {
    val bits = if (single) value.toFloat().toRawBits().toLong() else value.toRawBits()
    val width = if (single) 4 else 8
    val sign = if (single) Int.MIN_VALUE.toLong() else Long.MIN_VALUE
    val transformed = (bits xor ((bits shr (width * 8 - 1)) or sign)) + 1
    return ByteArray(width) { index ->
        val byte = (transformed ushr ((width - index - 1) * 8)).toByte()
        if (index == 0) (byte.toInt() xor 128).toByte() else byte
    }
}
