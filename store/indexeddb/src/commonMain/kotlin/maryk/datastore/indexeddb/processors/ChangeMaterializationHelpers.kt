package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsFuzzyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.NumberType
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.values.Values
import maryk.datastore.indexeddb.encodeStorageValue
import maryk.lib.extensions.compare.matchesRangePart
import maryk.datastore.shared.requireWritableValueReference
import maryk.datastore.shared.requireStoredReferenceParents
import maryk.datastore.shared.storageReferenceParents
import maryk.datastore.shared.IncrementingMapMaximum
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.areSameStoredScalarValues

internal fun <DM : IsRootDataModel> materializeChanges(
    changes: List<IsChange>,
    currentValues: Values<DM>,
    persistedMaxima: Map<Bytes, Comparable<Any>> = emptyMap(),
    allowMissingAllocationMap: Boolean = false,
) : MaterializedChanges {
    val appliedChanges = mutableListOf<IsChange>()
    val generatedChanges = mutableListOf<IsChange>()
    val latestGeneratedKeyByReference = mutableMapOf<Any, Comparable<Any>>()
    var stagedValues = currentValues
    val observedMaxima = mutableMapOf<Bytes, IncrementingMapMaximum>()

    fun observe(values: Values<DM>) {
        val maxima = try {
            collectIncrementingMapMaxima(values)
        } catch (error: IllegalArgumentException) {
            // Allocation tracking traverses intermediate values before final validation.
            // Report invalid input through its property validation rather than a codec error.
            values.validate()
            throw error
        }
        for (maximum in maxima) {
            val identity = Bytes(maximum.reference.toStorageByteArray())
            if (observedMaxima[identity]?.key?.let { it >= maximum.key } != true) observedMaxima[identity] = maximum
        }
    }
    observe(currentValues)

    for (change in changes) {
        if (change !is Change && change !is Check && change !is ListChange && change !is SetChange &&
            change !is IncMapChange && change !is ObjectSoftDeleteChange) {
            throw RequestException("Unsupported operation $change")
        }
        if (change !is IncMapChange) {
            appliedChanges += change
            if (change !is Check) {
                val next = applyMaterializedChanges(stagedValues, listOf(change), allowMissingAllocationMap)
                validateStagedChange(change, stagedValues, next, allowMissingAllocationMap)
                stagedValues = next
                observe(stagedValues)
            }
            continue
        }

        val additions = change.valueChanges.mapNotNull { valueChange ->
            val addValues = valueChange.addValues ?: return@mapNotNull null
            if (addValues.isEmpty()) return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val reference = valueChange.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>
            for ((index, value) in addValues.withIndex()) {
                reference.propertyDefinition.valueDefinition.validateWithRef(null, value) {
                    reference.propertyDefinition.addIndexRef(index, reference)
                }
            }
            if (valueChange.reference.propertyDefinition.definition.keyNumberDescriptor.type in
                setOf(NumberType.Float32Type, NumberType.Float64Type)) {
                throw RequestException("Automatic incrementing-map allocation requires an integer key descriptor")
            }

            if (valueChange.reference.propertyDefinition.final && stagedValues[valueChange.reference] != null) {
                throw AlreadySetException(valueChange.reference)
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
            val previous = stagedValues
            stagedValues = stagedValues.change(addition)
            for (part in additions) validateChangedCollectionPaths(part.reference, previous, stagedValues)
            observe(stagedValues)
        }
    }

    return MaterializedChanges(
        appliedChanges = appliedChanges,
        generatedChanges = generatedChanges,
        incrementingMapMaxima = observedMaxima.values.toList(),
    )
}

/** Validate supplied values even on deleted rows, and preserve predecessor-dependent final/type guards. */
@Suppress("UNCHECKED_CAST")
private fun <DM : IsRootDataModel> validateStagedChange(
    change: IsChange, before: Values<DM>, after: Values<DM>, allowMissingAllocationMap: Boolean,
) {
    when (change) {
        is Change -> {
            var staged = before
            for (pair in change.referenceValuePairs) {
                val reference = pair.reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
                val previous = staged[reference]
                val definition = reference.comparablePropertyDefinition
                if (pair.value != null) {
                    definition.validateWithRef(null, pair.value) { reference }
                }
                if (pair.value == null && reference is ListItemReference<*, *>) {
                    val parent = reference.parentReference
                    if (previous != null && parent?.propertyDefinition?.final == true) throw AlreadySetException(parent)
                } else if (!(pair.value == null && (reference is MapValueReference<*, *, *> || reference is SetItemReference<*, *>))) {
                    val predecessors = if (reference.unwrap().any { it is IsFuzzyReference } && previous is List<*>) previous else listOf(previous)
                    for (old in predecessors) {
                        if (old == null) continue
                        if (definition.final && (definition !is IsSimpleValueDefinition<*, *> ||
                                !areSameStoredScalarValues(definition, old, pair.value))) {
                            throw AlreadySetException(reference)
                        }
                        if (definition is IsMultiTypeDefinition<*, *, *> && definition.typeIsFinal &&
                            old is TypedValue<*, *> && pair.value is TypedValue<*, *> && old.type != (pair.value as TypedValue<*, *>).type) {
                            throw AlreadySetException(reference)
                        }
                    }
                }
                val next = applyMaterializedChanges(staged, listOf(Change(pair)), allowMissingAllocationMap)
                validateChangedCollectionPaths(reference, staged, next)
                staged = next
            }
        }
        is ListChange -> for (part in change.listValueChanges) {
            if (before[part.reference] == after[part.reference]) continue
            part.reference.propertyDefinition.validateWithRef(null, after[part.reference]) { part.reference }
            validateChangedCollectionPaths(part.reference, before, after)
            if (part.reference.propertyDefinition.final && before[part.reference] != null && before[part.reference] != after[part.reference]) {
                throw AlreadySetException(part.reference)
            }
        }
        is SetChange -> for (part in change.setValueChanges) {
            if (before[part.reference] == after[part.reference]) continue
            part.reference.propertyDefinition.validateWithRef(null, after[part.reference]) { part.reference }
            validateChangedCollectionPaths(part.reference, before, after)
            if (part.reference.propertyDefinition.final && before[part.reference] != null && before[part.reference] != after[part.reference]) {
                throw AlreadySetException(part.reference)
            }
        }
    }
}

private fun <DM : IsRootDataModel> storedSubtree(values: Values<DM>, reference: AnyPropertyReference): Map<Bytes, Bytes> {
    val prefix = reference.toStorageByteArray()
    return buildMap {
        values.writeToStorage { type, qualifier, definition, value ->
            if (qualifier.matchesRangePart(0, prefix)) {
                put(Bytes(qualifier), Bytes(encodeStorageValue(type, definition, value)))
            }
        }
    }
}

/** Validate the keys and cardinalities changed by a child write, including on deleted rows. */
@Suppress("UNCHECKED_CAST")
private fun <DM : IsRootDataModel> validateChangedCollectionPaths(
    reference: AnyPropertyReference,
    before: Values<DM>,
    after: Values<DM>,
) {
    for (parent in storageReferenceParents(reference)) {
        if (parent.comparablePropertyDefinition.final && before[parent] != null &&
            storedSubtree(before, parent) != storedSubtree(after, parent)) {
            throw AlreadySetException(parent)
        }
    }
    if (reference is MapValueReference<*, *, *> && before[reference] == null && after[reference] != null) {
        val entry = reference as MapValueReference<Any, Any, IsPropertyContext>
        entry.mapDefinition.keyDefinition.validateWithRef(null, entry.key) {
            entry.mapDefinition.keyRef(entry.key, entry.parentReference)
        }
    }
    fun size(value: Any?): UInt? = when (value) {
        is Map<*, *> -> value.size.toUInt()
        is Collection<*> -> value.size.toUInt()
        else -> null
    }
    for (part in listOf(reference) + storageReferenceParents(reference)) {
        val newSize = size(after[part]) ?: continue
        if (newSize == size(before[part])) continue
        when (val definition = part.comparablePropertyDefinition) {
            is IsMapDefinition<*, *, *> ->
                (definition as IsMapDefinition<Any, Any, IsPropertyContext>).validateSize(newSize) {
                    part as TypedPropertyReference<Map<Any, Any>>
                }
            is IsCollectionDefinition<*, *, *, *> ->
                (definition as IsCollectionDefinition<Any, Collection<Any>, IsPropertyContext,
                    IsSubDefinition<Any, IsPropertyContext>>).validateSize(newSize) {
                    part as TypedPropertyReference<Collection<Any>>
                }
        }
    }
}

/** Apply in order, initializing only a supported immediate map parent and preserving outer ancestors. */
@Suppress("UNCHECKED_CAST")
internal fun <DM : IsRootDataModel> applyMaterializedChanges(
    currentValues: Values<DM>,
    changes: List<IsChange>,
    allowMissingAllocationMap: Boolean = false,
): Values<DM> {
    var staged = currentValues
    for (change in changes) {
        if (change !is Change) {
            staged = staged.change(change)
            continue
        }
        for (pair in change.referenceValuePairs) {
            // Deleting an inactive case is an idempotent no-op, including during history replay.
            if (pair.value == null &&
                (pair.reference is TypedValueReference<*, *, *> || pair.reference is SimpleTypedValueReference<*, *, *>) &&
                staged[pair.reference as TypedPropertyReference<Any>] == null) continue
            requireWritableValueReference(pair.reference, pair.value) { staged[it] != null }
            if (pair.value != null) {
                val reference = pair.reference
                val parent = storageReferenceParents(reference).firstOrNull()
                val canInitializeMap = parent is IsMapReference<*, *, *, *> &&
                    (pair.value is Map<*, *> || pair.value is List<*> || pair.value is Set<*> ||
                        pair.value is Values<*> || pair.value is TypedValue<*, *> ||
                        (allowMissingAllocationMap && parent is IncMapReference<*, *, *>))
                requireStoredReferenceParents(reference,
                    hasValue = { staged[it] != null },
                    readCount = { (staged[it] as? List<*>)?.size },
                    allowMissingMapParent = canInitializeMap,
                )
                if (canInitializeMap && staged[parent] == null) {
                    val mapReference = parent as IsPropertyReference<Map<Any, Any>, IsMapDefinition<Any, Any, IsPropertyContext>, *>
                    staged = staged.change(Change(mapReference with emptyMap<Any, Any>()))
                }
            }
            staged = staged.change(Change(pair))
        }
    }
    return staged
}

internal fun <DM : IsRootDataModel> evaluateChecks(
    changes: List<IsChange>,
    currentValues: Values<DM>,
    allowMissingAllocationMap: Boolean = false,
): List<ValidationException> {
    if (changes.none { it is Check }) return emptyList()
    val exceptions = mutableListOf<ValidationException>()
    var stagedValues = currentValues

    for (change in changes) {
        if (change !is Check) {
            stagedValues = applyMaterializedChanges(stagedValues, listOf(change), allowMissingAllocationMap)
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
