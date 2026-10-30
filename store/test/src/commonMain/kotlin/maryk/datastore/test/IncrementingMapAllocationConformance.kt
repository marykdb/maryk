package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.string
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

object AllocationDescriptorRecord : RootDataModel<AllocationDescriptorRecord>() {
    val unsigned8 by incrementingMap(1u, required = false, keyNumberDescriptor = UInt8, valueDefinition = StringDefinition())
    val unsigned16 by incrementingMap(2u, required = false, keyNumberDescriptor = UInt16, valueDefinition = StringDefinition())
    val unsigned32 by incrementingMap(3u, required = false, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val unsigned64 by incrementingMap(4u, required = false, keyNumberDescriptor = UInt64, valueDefinition = StringDefinition())
    val signed8 by incrementingMap(5u, required = false, keyNumberDescriptor = SInt8, valueDefinition = StringDefinition())
    val signed16 by incrementingMap(6u, required = false, keyNumberDescriptor = SInt16, valueDefinition = StringDefinition())
    val signed32 by incrementingMap(7u, required = false, keyNumberDescriptor = SInt32, valueDefinition = StringDefinition())
    val signed64 by incrementingMap(8u, required = false, keyNumberDescriptor = SInt64, valueDefinition = StringDefinition())
    val float32 by incrementingMap(9u, required = false, keyNumberDescriptor = Float32, valueDefinition = StringDefinition())
    val float64 by incrementingMap(10u, required = false, keyNumberDescriptor = Float64, valueDefinition = StringDefinition())
    val marker by string(11u, required = false)
}

/** The persistent ordered-byte contract starts signed allocation at MIN+1 and unsigned at1. */
suspend fun assertIntegerIncrementingMapAllocation(store: IsDataStore) {
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { unsigned8 }, 1.toUByte(), 5.toUByte(), 6.toUByte(), null, null, (UByte.MAX_VALUE - 1u).toUByte(), UByte.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { unsigned16 }, 1.toUShort(), 5.toUShort(), 6.toUShort(), null, null, (UShort.MAX_VALUE - 1u).toUShort(), UShort.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { unsigned32 }, 1u, 5u, 6u, null, null, UInt.MAX_VALUE - 1u, UInt.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { unsigned64 }, 1uL, 5uL, 6uL, null, null, ULong.MAX_VALUE - 1uL, ULong.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { signed8 }, (Byte.MIN_VALUE + 1).toByte(), 5.toByte(), 6.toByte(), (-3).toByte(), (-2).toByte(), (Byte.MAX_VALUE - 1).toByte(), Byte.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { signed16 }, (Short.MIN_VALUE + 1).toShort(), 5.toShort(), 6.toShort(), (-3).toShort(), (-2).toShort(), (Short.MAX_VALUE - 1).toShort(), Short.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { signed32 }, Int.MIN_VALUE + 1, 5, 6, -3, -2, Int.MAX_VALUE - 1, Int.MAX_VALUE)
    checkIntegerAllocation(store, AllocationDescriptorRecord.ref { signed64 }, Long.MIN_VALUE + 1L, 5L, 6L, -3L, -2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE)
    val values = AllocationDescriptorRecord.create {
        float32 with mapOf(1.25f to "explicit float")
        float64 with mapOf(1.25 to "explicit double")
    }
    val added = assertIs<AddSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.add(values)).statuses.single())
    for (allocation in listOf(
        IncMapChange(AllocationDescriptorRecord.ref { float32 }.change(addValues = listOf("unsupported"))),
        IncMapChange(AllocationDescriptorRecord.ref { float64 }.change(addValues = listOf("unsupported"))),
    )) {
        assertIs<ServerFail<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(added.key.change(
            Change(AllocationDescriptorRecord.ref { marker } with "must roll back"), allocation,
        ))).statuses.single())
        val current = store.execute(AllocationDescriptorRecord.get(added.key)).values.single()
        assertEquals(values, current.values)
        assertEquals(added.version, current.lastVersion)
        assertNull(current.values { marker })
    }
    assertIs<ChangeSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(added.key.change(Change(
        AllocationDescriptorRecord.ref { float32 at 2.5f } with "another explicit float",
        AllocationDescriptorRecord.ref { float64 at 2.5 } with "another explicit double",
    )))).statuses.single())
    val explicit = store.execute(AllocationDescriptorRecord.get(added.key)).values.single().values
    assertEquals(mapOf(1.25f to "explicit float", 2.5f to "another explicit float"), explicit { float32 })
    assertEquals(mapOf(1.25 to "explicit double", 2.5 to "another explicit double"), explicit { float64 })
}

private suspend fun <K : Comparable<K>> checkIntegerAllocation(
    store: IsDataStore,
    reference: IncMapReference<K, String, *>,
    first: K,
    positive: K,
    positiveNext: K,
    negative: K?,
    negativeNext: K?,
    penultimate: K,
    maximum: K,
) {
    suspend fun add(entries: Map<K, String>) = assertIs<AddSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.add(
        AllocationDescriptorRecord.create { }.copyWithValue(reference.index, entries),
    )).statuses.single())
    val empty = add(emptyMap())
    assertIs<ChangeSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(empty.key.change(
        IncMapChange(reference.change(addValues = listOf("first"))),
    ))).statuses.single())
    assertEquals(mapOf(first to "first"), store.execute(AllocationDescriptorRecord.get(empty.key)).values.single().values[reference], reference.completeName)
    for ((start, next) in listOfNotNull(positive to positiveNext, negative?.let { it to requireNotNull(negativeNext) })) {
        val seeded = add(mapOf(start to "seed"))
        assertIs<ChangeSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(seeded.key.change(
            IncMapChange(reference.change(addValues = listOf("next"))),
        ))).statuses.single())
        assertEquals(mapOf(start to "seed", next to "next"), store.execute(AllocationDescriptorRecord.get(seeded.key)).values.single().values[reference], reference.completeName)
    }
    val nearLimit = add(mapOf(penultimate to "penultimate"))
    assertIs<ServerFail<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(nearLimit.key.change(
        IncMapChange(reference.change(addValues = listOf("last", "must not wrap"))),
    ))).statuses.single())
    val rejected = store.execute(AllocationDescriptorRecord.get(nearLimit.key)).values.single()
    assertEquals(mapOf(penultimate to "penultimate"), rejected.values[reference], reference.completeName)
    assertEquals(nearLimit.version, rejected.lastVersion, reference.completeName)
    assertIs<ChangeSuccess<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(nearLimit.key.change(
        IncMapChange(reference.change(addValues = listOf("last"))),
    ))).statuses.single())
    val last = store.execute(AllocationDescriptorRecord.get(nearLimit.key)).values.single()
    assertEquals(mapOf(penultimate to "penultimate", maximum to "last"), last.values[reference], reference.completeName)
    assertIs<ServerFail<AllocationDescriptorRecord>>(store.execute(AllocationDescriptorRecord.change(nearLimit.key.change(
        IncMapChange(reference.change(addValues = listOf("must not wrap"))),
    ))).statuses.single())
    assertEquals(last, store.execute(AllocationDescriptorRecord.get(nearLimit.key)).values.single(), reference.completeName)
}
