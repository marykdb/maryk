package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.references.dsl.at
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

private object RawUniqueDetails : DataModel<RawUniqueDetails>() {
    val double by number(1u, type = Float64, unique = true, required = false)
    val float by number(2u, type = Float32, unique = true, required = false)
}

private object RawUniqueRecord : RootDataModel<RawUniqueRecord>() {
    val double by number(1u, type = Float64, unique = true, required = false)
    val float by number(2u, type = Float32, unique = true, required = false)
    val details by embed(3u, dataModel = { RawUniqueDetails }, required = false)
    val children by map(4u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { RawUniqueDetails }), required = false)
}

class FloatingUniqueIdentityTest {
    private val doubles = listOf(0x7ff8000000000001L, 0x7ff8000000000002L, 0x7ff8000000000003L).map(Double::fromBits)
    private val floats = listOf(0x7fc00001, 0x7fc00002, 0x7fc00003).map(Float::fromBits)

    @Test
    fun float64UniqueClaimsRetainRawPayloadsThroughTheirLifecycle() = runTest {
        exerciseLifecycle(doubles,
            create = { value -> RawUniqueRecord.create { double with value } },
            change = { value -> Change(RawUniqueRecord.ref { double } with value) },
            filter = { value -> Equals(RawUniqueRecord.ref { double } with value) },
            read = { values -> values { double } }, bits = Double::toRawBits,
        )
    }

    @Test
    fun float32UniqueClaimsRetainRawPayloadsThroughTheirLifecycle() = runTest {
        exerciseLifecycle(floats,
            create = { value -> RawUniqueRecord.create { float with value } },
            change = { value -> Change(RawUniqueRecord.ref { float } with value) },
            filter = { value -> Equals(RawUniqueRecord.ref { float } with value) },
            read = { values -> values { float } }, bits = { it.toRawBits().toLong() },
        )
    }

    @Test
    fun replacingEmbeddedAndMapSubtreesTransfersRawFloat64Claims() = runTest {
        for (inMap in listOf(false, true)) {
            fun child(value: Double) = RawUniqueDetails.create { double with value }
            exerciseReplacement(doubles,
                create = { value -> RawUniqueRecord.create {
                    if (inMap) children with mapOf("fixed" to child(value)) else details with child(value)
                } },
                change = { value -> if (inMap) Change(RawUniqueRecord.ref { children } with mapOf("fixed" to child(value)))
                    else Change(RawUniqueRecord.ref { details } with child(value)) },
                filter = { value -> Equals(if (inMap) RawUniqueRecord.ref { children.at("fixed") { RawUniqueDetails.double } } with value
                    else RawUniqueRecord.ref { details { double } } with value) },
                read = { values -> (if (inMap) values { children }?.get("fixed") else values { details })?.get(RawUniqueDetails.ref { double }) },
                bits = Double::toRawBits,
            )
        }
    }

    @Test
    fun replacingEmbeddedAndMapSubtreesTransfersRawFloat32Claims() = runTest {
        for (inMap in listOf(false, true)) {
            fun child(value: Float) = RawUniqueDetails.create { float with value }
            exerciseReplacement(floats,
                create = { value -> RawUniqueRecord.create {
                    if (inMap) children with mapOf("fixed" to child(value)) else details with child(value)
                } },
                change = { value -> if (inMap) Change(RawUniqueRecord.ref { children } with mapOf("fixed" to child(value)))
                    else Change(RawUniqueRecord.ref { details } with child(value)) },
                filter = { value -> Equals(if (inMap) RawUniqueRecord.ref { children.at("fixed") { RawUniqueDetails.float } } with value
                    else RawUniqueRecord.ref { details { float } } with value) },
                read = { values -> (if (inMap) values { children }?.get("fixed") else values { details })?.get(RawUniqueDetails.ref { float }) },
                bits = { it.toRawBits().toLong() },
            )
        }
    }

    private suspend fun <T : Any> exerciseLifecycle(
        numbers: List<T>, create: (T) -> Values<RawUniqueRecord>, change: (T?) -> Change,
        filter: (T) -> Equals, read: (Values<RawUniqueRecord>) -> T?, bits: (T) -> Long,
    ) {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to RawUniqueRecord))
            suspend fun lookup(value: T) = store.execute(RawUniqueRecord.scan(where = filter(value))).also {
                assertIs<FetchByUniqueKey>(it.dataFetchType)
            }.values.map { it.key }
            try {
                val first = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                val second = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[1]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                assertEquals(listOf(first.key), lookup(numbers[0]))
                assertEquals(listOf(second.key), lookup(numbers[1]))
                assertEquals(emptyList(), lookup(numbers[2]))
                assertEquals(bits(numbers[0]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
                val changed = assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(numbers[2])))).statuses.single())
                assertEquals(emptyList(), lookup(numbers[0]))
                assertEquals(listOf(first.key), lookup(numbers[2]))
                val noOp = assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(numbers[2])))).statuses.single())
                assertEquals(changed.version, noOp.version)
                assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(numbers[1])))).statuses.single())
                assertEquals(bits(numbers[2]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
                assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(null)))).statuses.single())
                assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[2]))).statuses.single())
                assertIs<DeleteSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.delete(second.key)).statuses.single())
                assertEquals(emptyList(), lookup(numbers[1]))
                val replacement = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[1]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(second.key.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertIs<DeleteSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.delete(replacement.key, hardDelete = true)).statuses.single())
                assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(second.key.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertEquals(listOf(second.key), lookup(numbers[1]))
                if (keepHistory) {
                    val historic = store.execute(RawUniqueRecord.scan(where = filter(numbers[0]), toVersion = first.version))
                    assertEquals(listOf(first.key), historic.values.map { it.key })
                    assertEquals(bits(numbers[0]), bits(assertNotNull(read(historic.values.single().values))))
                }
            } finally { store.close() }
        }
    }

    private suspend fun <T : Any> exerciseReplacement(
        numbers: List<T>, create: (T) -> Values<RawUniqueRecord>, change: (T) -> Change,
        filter: (T) -> Equals, read: (Values<RawUniqueRecord>) -> T?, bits: (T) -> Long,
    ) {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to RawUniqueRecord))
            try {
                val first = assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[1]))).statuses.single())
                assertIs<ChangeSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(numbers[2])))).statuses.single())
                assertEquals(bits(numbers[2]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[2]))).statuses.single())
                val lookup = store.execute(RawUniqueRecord.scan(where = filter(numbers[2])))
                assertEquals(listOf(first.key), lookup.values.map { it.key }, "unique lookup: ${lookup.dataFetchType}, history=$keepHistory")
                assertEquals(emptyList(), store.execute(RawUniqueRecord.scan(where = filter(numbers[0]))).values)
                assertIs<AddSuccess<RawUniqueRecord>>(store.execute(RawUniqueRecord.add(create(numbers[0]))).statuses.single())
                assertIs<ValidationFail<RawUniqueRecord>>(store.execute(RawUniqueRecord.change(first.key.change(change(numbers[1])))).statuses.single())
                assertEquals(bits(numbers[2]), bits(assertNotNull(read(store.execute(RawUniqueRecord.get(first.key)).values.single().values))))
            } finally { store.close() }
        }
    }
}
