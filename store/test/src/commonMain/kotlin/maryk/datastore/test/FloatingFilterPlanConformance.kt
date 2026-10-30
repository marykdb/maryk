package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.set
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.ValueIn
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

object FloatingFilterPlanRecord : RootDataModel<FloatingFilterPlanRecord>(indexes = {
    listOf(FloatingFilterPlanRecord.indexedDouble.ref(), FloatingFilterPlanRecord.indexedFloat.ref())
}) {
    val double by number(1u, type = Float64, unique = true)
    val float by number(2u, type = Float32, unique = true)
    val indexedDouble by number(3u, type = Float64)
    val indexedFloat by number(4u, type = Float32)
    val members by set(5u, valueDefinition = NumberDefinition(type = Float64))
    val keyed by map(6u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
    val values by map(7u, keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = Float64))
    val sequence by list(8u, valueDefinition = NumberDefinition(type = Float64))
    val link by reference(9u, required = false, dataModel = { FloatingFilterPlanRecord })
}

/** A result set must not depend on choosing a unique, secondary, primary or point-read path. */
suspend fun assertFloatingFilterPlanEquivalence(store: IsDataStore, includeDistinctNaNPayloads: Boolean = true) {
    val doubleBits = listOf(0L, Long.MIN_VALUE, 1L, 1.0.toRawBits(),
        0x7ff8000000000001L, 0x7ff8000000000002L, 0x7ff8000000000003L, -2251799813685247L, -2251799813685246L)
    val floatBits = listOf(0, Int.MIN_VALUE, 1, 1.0f.toRawBits(),
        0x7fc00001, 0x7fc00002, 0x7fc00003, -4194303, -4194302)
    val doubles = (if (includeDistinctNaNPayloads) doubleBits else doubleBits.take(4) + 0x7ff8000000000000L).map(Double::fromBits)
    val floats = (if (includeDistinctNaNPayloads) floatBits else floatBits.take(4) + 0x7fc00000).map(Float::fromBits)
    val added = doubles.indices.map { index ->
        assertIs<AddSuccess<FloatingFilterPlanRecord>>(store.execute(FloatingFilterPlanRecord.add(
            FloatingFilterPlanRecord.create {
                double with doubles[index]
                float with floats[index]
                indexedDouble with doubles[index]
                indexedFloat with floats[index]
                members with setOf(doubles[index])
                keyed with mapOf(doubles[index] to "present")
                values with mapOf("value" to doubles[index])
                sequence with listOf(doubles[index])
            },
        )).statuses.single())
    }
    val keys = added.map { it.key }
    val linked = added.map { addedRecord ->
        assertIs<ChangeSuccess<FloatingFilterPlanRecord>>(store.execute(FloatingFilterPlanRecord.change(addedRecord.key.change(
            Change(FloatingFilterPlanRecord.ref { link } with addedRecord.key),
        ))).statuses.single())
    }
    val snapshot = linked.last().version

    suspend fun checkPaths(filter: IsFilter, expected: Set<Key<FloatingFilterPlanRecord>>, version: ULong? = null) {
        val table = store.execute(FloatingFilterPlanRecord.scan(where = filter, filterSoftDeleted = false,
            allowTableScan = true, toVersion = version))
        assertIs<FetchByTableScan>(table.dataFetchType)
        assertEquals(expected, table.values.map { it.key }.toSet(), "table filter=$filter version=$version")
        assertEquals(expected, store.execute(FloatingFilterPlanRecord.get(*keys.toTypedArray(), where = filter,
            filterSoftDeleted = false, toVersion = version)).values.map { it.key }.toSet(), "point filter=$filter version=$version")
        assertEquals(expected, store.execute(FloatingFilterPlanRecord.scan(where = Or(filter),
            filterSoftDeleted = false, allowTableScan = true, toVersion = version)).values.map { it.key }.toSet(), "Or filter=$filter version=$version")
        assertEquals(keys.toSet() - expected, store.execute(FloatingFilterPlanRecord.scan(where = Not(filter),
            filterSoftDeleted = false, allowTableScan = true, toVersion = version)).values.map { it.key }.toSet(), "Not filter=$filter version=$version")
    }
    suspend fun checkAll(version: ULong?) {
        for (index in doubles.indices) {
            val expected = setOf(keys[index])
            val rawDouble = Equals(FloatingFilterPlanRecord.ref { double } with doubles[index])
            val rawFloat = Equals(FloatingFilterPlanRecord.ref { float } with floats[index])
            for (filter in listOf(rawDouble, rawFloat,
                Equals(FloatingFilterPlanRecord.ref { link { double } } with doubles[index]),
                Equals(FloatingFilterPlanRecord.ref { link { float } } with floats[index]),
                ValueIn(FloatingFilterPlanRecord.ref { link { double } } with setOf(doubles[index])),
                ValueIn(FloatingFilterPlanRecord.ref { link { float } } with setOf(floats[index])),
                ValueIn(FloatingFilterPlanRecord.ref { double } with setOf(doubles[index])),
                ValueIn(FloatingFilterPlanRecord.ref { float } with setOf(floats[index])))) {
                checkPaths(filter, expected, version)
            }
            for (filter in listOf(rawDouble, rawFloat)) {
                val unique = store.execute(FloatingFilterPlanRecord.scan(where = filter, toVersion = version))
                assertIs<FetchByUniqueKey>(unique.dataFetchType)
                assertEquals(expected, unique.values.map { it.key }.toSet(), "unique filter=$filter version=$version")
            }
            val indexedDouble = store.execute(FloatingFilterPlanRecord.scan(
                where = Equals(FloatingFilterPlanRecord.ref { indexedDouble } with doubles[index]),
                order = FloatingFilterPlanRecord.ref { indexedDouble }.ascending(), toVersion = version))
            assertIs<FetchByIndexScan>(indexedDouble.dataFetchType)
            assertEquals(expected, indexedDouble.values.map { it.key }.toSet(), "double secondary index=$index version=$version")
            val indexedFloat = store.execute(FloatingFilterPlanRecord.scan(
                where = Equals(FloatingFilterPlanRecord.ref { indexedFloat } with floats[index]),
                order = FloatingFilterPlanRecord.ref { indexedFloat }.ascending(), toVersion = version))
            assertIs<FetchByIndexScan>(indexedFloat.dataFetchType)
            assertEquals(expected, indexedFloat.values.map { it.key }.toSet(), "float secondary index=$index version=$version")
        }
    }
    checkAll(null)
    assertIs<DeleteSuccess<FloatingFilterPlanRecord>>(store.execute(FloatingFilterPlanRecord.delete(keys.last())).statuses.single())
    checkAll(snapshot)
    val nanKeys = doubles.indices.filter { doubles[it].isNaN() }.map { keys[it] }.toSet()
    for (index in doubles.indices) {
        val value = doubles[index]
        val qualifierKeys = if (value.isNaN()) nanKeys else setOf(keys[index])
        checkPaths(Equals(FloatingFilterPlanRecord.ref { members.any() } with value), qualifierKeys)
        checkPaths(Equals(FloatingFilterPlanRecord.ref { keyed.anyKey() } with value), qualifierKeys)
        checkPaths(Equals(FloatingFilterPlanRecord.ref { values.anyValue() } with value), setOf(keys[index]))
        checkPaths(Equals(FloatingFilterPlanRecord.ref { sequence.any() } with value), setOf(keys[index]))
    }
}
