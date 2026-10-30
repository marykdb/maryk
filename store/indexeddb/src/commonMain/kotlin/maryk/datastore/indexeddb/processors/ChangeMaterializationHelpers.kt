package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.values.Values
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.IncrementingMapMaximum



internal fun <DM : IsRootDataModel> materializeChanges(
    changes: List<IsChange>,
    currentValues: Values<DM>,
    persistedMaxima: Map<Bytes, Comparable<Any>> = emptyMap(),
) : MaterializedChanges {
    val appliedChanges = mutableListOf<IsChange>()
    val generatedChanges = mutableListOf<IsChange>()
    val latestGeneratedKeyByReference = mutableMapOf<Any, Comparable<Any>>()
    var stagedValues = currentValues
    val observedMaxima = mutableMapOf<Bytes, IncrementingMapMaximum>()

    fun observe(values: Values<DM>) {
        for (maximum in collectIncrementingMapMaxima(values)) {
            val identity = Bytes(maximum.reference.toStorageByteArray())
            if (observedMaxima[identity]?.key?.let { it >= maximum.key } != true) observedMaxima[identity] = maximum
        }
    }
    observe(currentValues)

    for (change in changes) {
        if (change !is IncMapChange) {
            appliedChanges += change
            if (change !is Check) {
                stagedValues = seedMissingRootMaps(stagedValues, listOf(change)).change(change)
                observe(stagedValues)
            }
            continue
        }

        val additions = change.valueChanges.mapNotNull { valueChange ->
            val addValues = valueChange.addValues ?: return@mapNotNull null
            if (addValues.isEmpty()) return@mapNotNull null
            if (valueChange.reference.propertyDefinition.definition.keyNumberDescriptor.type in
                setOf(NumberType.Float32Type, NumberType.Float64Type)) {
                throw RequestException("Automatic incrementing-map allocation requires an integer key descriptor")
            }

            val currentMap = stagedValues[valueChange.reference].orEmptyComparableMap()
            val referenceIdentity = Bytes(valueChange.reference.toStorageByteArray())
            val currentMaxKey = listOfNotNull(
                latestGeneratedKeyByReference[valueChange.reference],
                currentMap.keys.maxOrNull(),
                persistedMaxima[referenceIdentity],
                observedMaxima[referenceIdentity]?.key,
            ).maxOrNull()
                ?: zeroComparableKeyFor(valueChange.reference)
            val nextKeys = ArrayList<Comparable<Any>>(addValues.size)
            var nextKey = currentMaxKey

            repeat(addValues.size) {
                val previous = nextKey
                nextKey = nextComparableKey(valueChange.reference, previous)
                if (nextKey <= previous) throw RequestException("Incrementing map key space is exhausted")
                nextKeys += nextKey
            }
            latestGeneratedKeyByReference[valueChange.reference] = nextKey

            createIncMapKeyAdditions(
                valueChange.reference,
                nextKeys.zip(addValues).map { (key, value) ->
                    ComparableMapEntry(key, value)
                }
            )
        }

        if (additions.isNotEmpty()) {
            val addition = IncMapAddition(additions)
            appliedChanges += addition
            generatedChanges += addition
            stagedValues = stagedValues.change(addition)
            observe(stagedValues)
        }
    }

    return MaterializedChanges(
        appliedChanges = appliedChanges,
        generatedChanges = generatedChanges,
        incrementingMapMaxima = observedMaxima.values.toList(),
    )
}

internal fun <DM : IsRootDataModel> seedMissingRootMaps(
    currentValues: Values<DM>,
    changes: List<IsChange>,
): Values<DM> {
    var seededValues = currentValues
    val seen = mutableSetOf<UInt>()

    for (change in changes) {
        if (change !is Change) continue

        for (pair in change.referenceValuePairs) {
            if (pair.value == null) continue
            val reference = pair.reference as? MapValueReference<*, *, *> ?: continue
            val mapReference = reference.parentReference as? MapReference<*, *, *> ?: continue
            if (currentValues[mapReference] != null || !seen.add(mapReference.index)) continue

            seededValues = seededValues.copyWithValue(mapReference.index, emptyMap<Any, Any>())
        }
    }

    return seededValues
}

internal fun <DM : IsRootDataModel> evaluateChecks(
    changes: List<IsChange>,
    currentValues: Values<DM>,
): List<ValidationException> {
    if (changes.none { it is Check }) return emptyList()
    val exceptions = mutableListOf<ValidationException>()
    var stagedValues = currentValues

    for (change in changes) {
        if (change !is Check) {
            stagedValues = seedMissingRootMaps(stagedValues, listOf(change)).change(change)
            continue
        }

        for ((reference, value) in change.referenceValuePairs) {
            if (stagedValues[reference] != value) {
                exceptions += InvalidValueException(reference, value.toString())
            }
        }
    }

    return exceptions
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.orEmptyComparableMap(): Map<Comparable<Any>, Any> =
    (this as? Map<Comparable<Any>, Any>).orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun zeroComparableKeyFor(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any> = when (reference.propertyDefinition.definition.keyNumberDescriptor.type) {
    NumberType.UInt8Type -> 0.toUByte()
    NumberType.UInt16Type -> 0.toUShort()
    NumberType.UInt32Type -> 0u
    NumberType.UInt64Type -> 0uL
    NumberType.SInt8Type -> Byte.MIN_VALUE
    NumberType.SInt16Type -> Short.MIN_VALUE
    NumberType.SInt32Type -> Int.MIN_VALUE
    NumberType.SInt64Type -> Long.MIN_VALUE
    else -> throw RequestException("Automatic incrementing-map allocation requires an integer key descriptor")
} as Comparable<Any>

@Suppress("UNCHECKED_CAST")
internal fun nextComparableKey(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
    value: Comparable<Any>,
): Comparable<Any> = when (reference.propertyDefinition.definition.keyNumberDescriptor.type) {
    NumberType.UInt8Type -> ((value as UByte) + 1u).toUByte()
    NumberType.UInt16Type -> ((value as UShort) + 1u).toUShort()
    NumberType.UInt32Type -> (value as UInt) + 1u
    NumberType.UInt64Type -> (value as ULong) + 1uL
    NumberType.SInt8Type -> ((value as Byte) + 1).toByte()
    NumberType.SInt16Type -> ((value as Short) + 1).toShort()
    NumberType.SInt32Type -> (value as Int) + 1
    NumberType.SInt64Type -> (value as Long) + 1L
    else -> throw RequestException("Automatic incrementing-map allocation requires an integer key descriptor")
} as Comparable<Any>

@Suppress("UNCHECKED_CAST")
internal fun createIncMapKeyAdditions(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
    addedEntries: List<ComparableMapEntry>,
): IncMapKeyAdditions<out Comparable<Any>, out Any> {
    return IncMapKeyAdditions(
        reference = reference as IncMapReference<Comparable<Any>, Any, *>,
        addedKeys = addedEntries.map { it.key },
        addedValues = addedEntries.map { it.value }
    ) as IncMapKeyAdditions<out Comparable<Any>, out Any>
}

internal data class ComparableMapEntry(
    override val key: Comparable<Any>,
    override val value: Any,
) : Map.Entry<Comparable<Any>, Any>

internal fun <DM : IsRootDataModel> createAlreadyExistsException(
    dataModel: DM,
    uniqueException: UniqueException,
): AlreadyExistsException {
    var index = 0
    val reference = dataModel.getPropertyReferenceByStorageBytes(
        length = uniqueException.reference.size,
        reader = { uniqueException.reference[index++] }
    )
    return AlreadyExistsException(reference, uniqueException.key)
}
