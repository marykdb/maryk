package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.types.numeric.NumberType
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



internal fun <DM : IsRootDataModel> materializeChanges(
    changes: List<IsChange>,
    currentValues: Values<DM>,
) : MaterializedChanges {
    val appliedChanges = mutableListOf<IsChange>()
    val generatedChanges = mutableListOf<IsChange>()
    val latestGeneratedKeyByReference = mutableMapOf<Any, Comparable<Any>>()

    for (change in changes) {
        if (change !is IncMapChange) {
            appliedChanges += change
            continue
        }

        val additions = change.valueChanges.mapNotNull { valueChange ->
            val addValues = valueChange.addValues ?: return@mapNotNull null
            if (addValues.isEmpty()) return@mapNotNull null

            val currentMap = currentValues[valueChange.reference].orEmptyComparableMap()
            val currentMaxKey = latestGeneratedKeyByReference[valueChange.reference]
                ?: currentMap.keys.maxOrNull()
                ?: zeroComparableKeyFor(valueChange.reference)
            val nextKeys = ArrayList<Comparable<Any>>(addValues.size)
            var nextKey = currentMaxKey

            repeat(addValues.size) {
                nextKey = nextComparableKey(nextKey)
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
        }
    }

    return MaterializedChanges(
        appliedChanges = appliedChanges,
        generatedChanges = generatedChanges,
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
    val exceptions = mutableListOf<ValidationException>()

    for (change in changes) {
        if (change !is Check) continue

        for ((reference, value) in change.referenceValuePairs) {
            if (currentValues[reference] != value) {
                exceptions += InvalidValueException(reference, value.toString())
            }
        }
    }

    return exceptions
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.orEmptyComparableMap(): Map<Comparable<Any>, Any> =
    this as? Map<Comparable<Any>, Any> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun zeroComparableKeyFor(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any> = when (reference.propertyDefinition.definition.keyNumberDescriptor.type) {
    NumberType.UInt8Type -> 0.toUByte()
    NumberType.UInt16Type -> 0.toUShort()
    NumberType.UInt32Type -> 0u
    NumberType.UInt64Type -> 0uL
    NumberType.SInt8Type -> 0.toByte()
    NumberType.SInt16Type -> 0.toShort()
    NumberType.SInt32Type -> 0
    NumberType.SInt64Type -> 0L
    else -> error("Unsupported incrementing map key type ${reference.propertyDefinition.definition.keyNumberDescriptor.type}")
} as Comparable<Any>

@Suppress("UNCHECKED_CAST")
internal fun nextComparableKey(value: Comparable<Any>): Comparable<Any> = when (value) {
    is UByte -> (value + 1u).toUByte()
    is UShort -> (value + 1u).toUShort()
    is UInt -> value + 1u
    is ULong -> value + 1u
    is Byte -> (value + 1).toByte()
    is Short -> (value + 1).toShort()
    is Int -> value + 1
    is Long -> value + 1
    else -> error("Unsupported incrementing map key type ${value::class}")
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
