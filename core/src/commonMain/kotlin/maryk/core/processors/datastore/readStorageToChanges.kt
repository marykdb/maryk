package maryk.core.processors.datastore

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.StorageException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.processors.datastore.ChangeType.CHANGE
import maryk.core.processors.datastore.ChangeType.EMBED_EXISTS
import maryk.core.processors.datastore.ChangeType.OBJECT_CREATE
import maryk.core.processors.datastore.ChangeType.OBJECT_DELETE
import maryk.core.processors.datastore.ChangeType.SET_ADD
import maryk.core.processors.datastore.ChangeType.TYPE
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.AnyValuePropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ObjectDeleteReference
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.DELETE
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SetValueChanges
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.ReferenceNullPair
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.MutableValueItems
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

typealias ValueWithVersionReader = (StorageTypeEnum<IsPropertyDefinition<out Any>>, IsPropertyReferenceForCache<*, *>, (ULong, Any?) -> Unit) -> Unit
private typealias ChangeAdder = (ULong, ChangeType, Any) -> Unit

private enum class ChangeType {
    OBJECT_CREATE, OBJECT_DELETE, CHANGE, EMBED_EXISTS, SET_ADD, TYPE
}

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM : IsRootDataModel> DM.readStorageToChanges(
    getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean,
    select: RootPropRefGraph<DM>?,
    creationVersion: ULong?,
    processValue: ValueWithVersionReader
): List<VersionedChanges> = readStorageToChangesImpl(getQualifier, select, creationVersion, null, processValue)

/**
 * Export replayable structural changes from stored cells. The snapshot reader must
 * return the complete selected projection at the requested version, independently of history bounds
 * or pagination. This preserves ordinary request list-change semantics on the receiver.
 */
fun <DM : IsRootDataModel> DM.readStorageToChanges(
    getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean,
    select: RootPropRefGraph<DM>?,
    creationVersion: ULong?,
    readValuesAtVersion: (ULong) -> Values<DM>?,
    processValue: ValueWithVersionReader
): List<VersionedChanges> = readStorageToChangesImpl(getQualifier, select, creationVersion, readValuesAtVersion, processValue)

private fun <DM : IsRootDataModel> DM.readStorageToChangesImpl(
    getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean,
    select: RootPropRefGraph<DM>?,
    creationVersion: ULong?,
    readValuesAtVersion: ((ULong) -> Values<DM>?)?,
    processValue: ValueWithVersionReader,
): List<VersionedChanges> {
    // Used to collect all found ValueItems
    val mutableVersionedChanges = mutableListOf<VersionedChanges>()
    val embeddedPresence = mutableMapOf<ULong, MutableSet<AnyPropertyReference>>()
    val collectionRoots = mutableMapOf<ULong, MutableSet<AnyPropertyReference>>()

    // Add changes to versionedChangesCollection
    val changeAdder: ChangeAdder = { version, changeType, changePart ->
        val index = mutableVersionedChanges.binarySearch { it.version compareTo version }

        // A child change already proves that its embedded parents exist. Keeping an
        // empty parent assignment as well would replace unchanged siblings inside
        // maps, lists and typed values when replaying a partial historical delta.
        if (changePart is IsReferenceValueOrNullPair<*> && index >= 0) {
            val parents = changePart.reference.unwrap().dropLast(1)
            val redundant = embeddedPresence[version]?.filter { it in parents }.orEmpty()
            if (redundant.isNotEmpty()) {
                for (change in mutableVersionedChanges[index].changes.filterIsInstance<Change>()) {
                    @Suppress("UNCHECKED_CAST")
                    (change.referenceValuePairs as MutableList<IsReferenceValueOrNullPair<Any>>).removeAll { it.reference in redundant }
                }
                embeddedPresence[version]?.removeAll(redundant)
            }
        }
        if (changeType == EMBED_EXISTS) {
            embeddedPresence.getOrPut(version) { mutableSetOf() }.add((changePart as IsReferenceValueOrNullPair<*>).reference)
        }

        if (index < 0) {
            mutableVersionedChanges.add(
                (index * -1) - 1,
                VersionedChanges(version, mutableListOf(createChange(changeType, changePart)))
            )
        } else {
            (mutableVersionedChanges[index].changes as MutableList<IsChange>).addChange(changeType, changePart)
        }
    }

    creationVersion?.let { changeAdder(it, OBJECT_CREATE, Unit) }

    val valueReader: ValueWithVersionReader = if (readValuesAtVersion == null) processValue else { type, reference, reader ->
        processValue(type, reference) { version, value ->
            val embeddedExists = value == Unit && (reference as? AnyPropertyReference)?.comparablePropertyDefinition is IsEmbeddedValuesDefinition<*, *>
            if (value != null && (type == ListSize || type == SetSize || type == MapSize || embeddedExists)) {
                collectionRoots.getOrPut(version) { mutableSetOf() }.add(reference as AnyPropertyReference)
                val versionIndex = mutableVersionedChanges.binarySearch { it.version compareTo version }
                if (versionIndex < 0) mutableVersionedChanges.add(-versionIndex - 1, VersionedChanges(version, mutableListOf()))
            }
            reader(version, value)
        }
    }

    processQualifiers(getQualifier) { qualifierReader, qualifierLength, addToCache ->
        // Otherwise, try to get a new qualifier processor from DataModel
        this.readQualifier(qualifierReader, qualifierLength, 0, select, null, changeAdder, valueReader, addToCache)
    }

    // Create Values
    return if (readValuesAtVersion == null) mutableVersionedChanges else mutableVersionedChanges.map { versioned ->
        val indexedRoots = versioned.changes.flatMap { change ->
            when (change) {
                is Change -> change.referenceValuePairs.map { it.reference }
                is MultiTypeChange -> change.referenceTypePairs.map { it.reference }
                is SetChange -> change.setValueChanges.map { it.reference }
                else -> emptyList()
            }
        }.mapNotNull { reference ->
            reference.unwrap().filterIsInstance<ListItemReference<*, *>>().firstOrNull()?.parentReference
        }
        val candidates = (indexedRoots + collectionRoots[versioned.version].orEmpty()).map { reference ->
            // A case assignment cannot create a missing typed parent by itself.
            reference.unwrap().filterIsInstance<TypedValueReference<*, *, *>>().firstOrNull()?.parentReference ?: reference
        }.distinct()
        val roots = candidates.filter { candidate ->
            candidates.none { other -> other != candidate && candidate.unwrap().any { it == other } }
        }
        if (roots.isEmpty()) versioned else {
            val snapshot = readValuesAtVersion(versioned.version)
                ?: throw StorageException("Missing record snapshot for committed structure version ${versioned.version}")
            val emitted = mutableSetOf<AnyPropertyReference>()
            val replacements = mutableListOf<IsReferenceValueOrNullPair<Any>>()
            fun replaceCollection(reference: AnyPropertyReference): Boolean {
                val root = roots.firstOrNull { candidate -> reference.unwrap().any { it == candidate } } ?: return false
                if (emitted.add(root)) {
                    @Suppress("UNCHECKED_CAST")
                    val collectionReference = root as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                    // An absent collection belongs to a deleted ancestor or an old typed case.
                    snapshot[collectionReference]?.let { value ->
                        if (value !is List<*> && value !is Map<*, *> && value !is Set<*> && value !is Values<*> && value !is TypedValue<*, *>) throw StorageException("Snapshot reference $root is not a structure")
                        replacements += ReferenceValuePair(collectionReference, value)
                    }
                }
                return true
            }
            val result = mutableListOf<IsChange>()
            for (change in versioned.changes) {
                val retained = when (change) {
                    is Change -> change.referenceValuePairs.filterNot { replaceCollection(it.reference) }.takeIf { it.isNotEmpty() }?.let(::Change)
                    is MultiTypeChange -> change.referenceTypePairs.filterNot { replaceCollection(it.reference) }.takeIf { it.isNotEmpty() }?.let(::MultiTypeChange)
                    is SetChange -> change.setValueChanges.filterNot { replaceCollection(it.reference) }.takeIf { it.isNotEmpty() }?.let(::SetChange)
                    else -> change
                }
                if (replacements.isNotEmpty()) {
                    result += Change(replacements.toMutableList())
                    replacements.clear()
                }
                if (retained != null) result += retained
            }
            // A changed count can be the only changed cell in an otherwise identical collection.
            for (root in roots) replaceCollection(root)
            if (replacements.isNotEmpty()) result += Change(replacements)
            VersionedChanges(versioned.version, result)
        }
    }
}

/** Adds change to existing list or creates a new change*/
private fun MutableList<IsChange>.addChange(changeType: ChangeType, changePart: Any) {
    @Suppress("UNCHECKED_CAST")
    when (changeType) {
        OBJECT_CREATE -> this.find { it is ObjectCreate }
        OBJECT_DELETE -> this.find { it is ObjectSoftDeleteChange }
        CHANGE, EMBED_EXISTS -> this.find { it is Change }?.also { change ->
            if (changePart is ReferenceNullPair<*>) {
                // Handle deletion of reference
                val reference = changePart.reference
                if (reference is IsPropertyReferenceWithParent<*, *, *, *>) {
                    val pairs = (change as Change).referenceValuePairs.map(IsReferenceValueOrNullPair<*>::reference)
                    var toSearch: AnyPropertyReference? = reference
                    while (toSearch is IsPropertyReferenceWithParent<*, *, *, *>) {
                        if (pairs.contains(toSearch as AnyValuePropertyReference)) {
                            return@also
                        }

                        toSearch = toSearch.parentReference ?: break
                    }
                }
            }
            ((change as Change).referenceValuePairs as MutableList<IsReferenceValueOrNullPair<*>>).add(
                changePart as IsReferenceValueOrNullPair<*>
            )
        }
        TYPE -> this.find { it is MultiTypeChange }?.also {
            ((it as MultiTypeChange).referenceTypePairs as MutableList<ReferenceTypePair<*>>).add(
                changePart as ReferenceTypePair<*>
            )
        }
        SET_ADD -> {
            this.find { it is SetChange }?.also { change ->
                val ref = changePart as SetItemReference<*, *>
                val setValueChanges = ((change as SetChange).setValueChanges as MutableList<SetValueChanges<*>>)

                when(val setValueChange = setValueChanges.find { it.reference == ref.parentReference }) {
                    null -> setValueChanges.add(
                        SetValueChanges(
                            ref.parentReference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *>,
                            addValues = mutableSetOf(ref.value)
                        )
                    )
                    else -> (setValueChange.addValues as MutableSet<Any>).add(ref.value)
                }
            }
        }
    } ?: this.add(createChange(changeType, changePart))
}

@Suppress("UNCHECKED_CAST")
private fun createChange(changeType: ChangeType, changePart: Any) = when (changeType) {
    OBJECT_CREATE -> ObjectCreate
    OBJECT_DELETE -> ObjectSoftDeleteChange(changePart as Boolean)
    CHANGE, EMBED_EXISTS -> Change(mutableListOf(changePart as IsReferenceValueOrNullPair<Any>))
    TYPE -> MultiTypeChange(mutableListOf(changePart as ReferenceTypePair<*>))
    SET_ADD -> {
        val ref = changePart as SetItemReference<*, *>
        SetChange(
            mutableListOf(
                SetValueChanges(
                    ref.parentReference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *>,
                    addValues = mutableSetOf(ref.value)
                )
            )
        )
    }
}

/**
 * Read specific [qualifierReader] from [offset].
 * [addChangeToOutput] is used to add changes to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache, so it does not need to reprocess the qualifier from start
 */
private fun <DM : IsDataModel> DM.readQualifier(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    select: IsPropRefGraph<*>?,
    parentReference: AnyPropertyReference?,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var currentOffset = offset

    initUIntByVarWithExtraInfo({ qualifierReader(currentOffset++) }) { index, type ->
        when (val refStoreType = referenceStorageTypeOf(type)) {
            DELETE -> {
                if (qualifierLength == 1) {
                    readValueFromStorage(ObjectDelete, ObjectDeleteReference) { version, value ->
                        if (value != null) {
                            addChangeToOutput(version, OBJECT_DELETE, value)
                        }
                    }
                } else Unit
            }
            else -> {
                val definition = this[index]
                    ?: throw DefNotFoundException("No definition for $index in $this at $index")

                val ref = definition.ref(parentReference)

                if (select?.contains(ref) == false) {
                    null // Skip since not in select
                } else {
                    readQualifierOfType(
                        qualifierReader,
                        qualifierLength,
                        currentOffset,
                        definition.definition,
                        refStoreType,
                        index,
                        select,
                        ref,
                        addChangeToOutput,
                        readValueFromStorage,
                        addToCache
                    )
                }
            }
        }
    }
}

/** Read qualifier from [qualifierReader] at [currentOffset] with [definition] into changes */
private fun <DM : IsValuesDataModel> readQualifierOfType(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    currentOffset: Int,
    definition: IsPropertyDefinition<out Any>,
    refStoreType: ReferenceType,
    index: UInt,
    select: IsPropRefGraph<DM>?,
    reference: IsPropertyReference<*, *, *>,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var offset = currentOffset
    val isAtEnd = qualifierLength <= offset

    when (refStoreType) {
        DELETE -> {
            // skip
        }
        VALUE -> {
            if (isAtEnd) {
                readValueFromStorage(Value, reference) { version, value ->
                    @Suppress("UNCHECKED_CAST")
                    val ref =
                        reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                    if (value == null) {
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(ref))
                    } else {
                        if (value !is TypedValue<*, *> && value !is MultiTypeEnum<*>) {
                            addChangeToOutput(version, CHANGE, ReferenceValuePair(ref, value))
                        } else { // Is a TypedValue with Unit as value
                            @Suppress("UNCHECKED_CAST")
                            readTypedValue(
                                qualifierReader,
                                qualifierLength,
                                offset,
                                readValueFromStorage,
                                definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                                ref,
                                select,
                                addToCache,
                                addChangeToOutput
                            )
                        }
                    }
                }
            } else { // Is Complex value
                // Only multi types can be encoded as complex VALUE qualifier
                if (definition !is IsMultiTypeDefinition<*, *, *>) {
                    throw ParseException("Only Multi types are allowed to be encoded as VALUE type with complex qualifiers. Not $definition")
                }
                @Suppress("UNCHECKED_CAST")
                readTypedValue(
                    qualifierReader,
                    qualifierLength,
                    offset,
                    readValueFromStorage,
                    definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                    reference,
                    select,
                    addToCache,
                    addChangeToOutput
                )
            }
        }
        EMBED -> {
            if (isAtEnd) {
                // Handle embed deletes
                readValueFromStorage(Value, reference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else {
                        val embeddedDefinition = definition as? IsEmbeddedValuesDefinition<*, *>
                            ?: throw TypeException("Only embedded definitions can have an existence qualifier. Not $definition")
                        addChangeToOutput.addStructure(version, reference, Values(embeddedDefinition.dataModel, MutableValueItems()), embeddedPresence = true)
                    }
                }
            } else {
                // Only embedded types can be encoded as complex EMBED qualifier
                if (definition !is IsEmbeddedValuesDefinition<*, *>) {
                    throw TypeException("Only Embeds types are allowed to be encoded as EMBED type with complex qualifiers. Not $definition")
                }
                readEmbeddedValues(
                    qualifierReader,
                    qualifierLength,
                    offset,
                    readValueFromStorage,
                    definition,
                    select,
                    reference,
                    addToCache,
                    addChangeToOutput
                )
            }
        }
        LIST -> {
            if (isAtEnd) {
                readValueFromStorage(ListSize, reference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else if (value == 0) {
                        addChangeToOutput.addStructure(version, reference, emptyList<Any>())
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val listDefinition = definition as IsListDefinition<Any, IsPropertyContext>
                val listReference = reference as CanContainListItemReference<*, *, *>

                val listIndex = initUInt({ qualifierReader(offset++) })

                val itemReference = listDefinition.itemRef(listIndex, listReference)

                val valueDefinition = listDefinition.valueDefinition
                if (qualifierLength > offset || valueDefinition is IsMultiTypeDefinition<*, *, *>) {
                    readComplexChanges(
                        qualifierReader, qualifierLength, offset, valueDefinition, itemReference,
                        select, addToCache, addChangeToOutput, readValueFromStorage,
                    )
                    return
                }

                readValueFromStorage(Value, itemReference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(itemReference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else if (valueDefinition is IsEmbeddedValuesDefinition<*, *>) {
                        addChangeToOutput.addStructure(version, itemReference, Values(valueDefinition.dataModel, MutableValueItems()), embeddedPresence = true)
                    } else {
                        addChangeToOutput(
                            version,
                            CHANGE,
                            itemReference with value
                        )
                    }
                }
            }
        }
        SET -> {
            if (isAtEnd) {
                readValueFromStorage(SetSize, reference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else if (value == 0) {
                        addChangeToOutput.addStructure(version, reference, emptySet<Any>())
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val setDefinition = definition as IsSetDefinition<Any, IsPropertyContext>
                val setReference = reference as CanContainSetItemReference<*, *, *>

                // Read set contents. It is always a simple value for set since it is in the qualifier.
                val valueDefinition =
                    ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                val setItemLength = readQualifierLength(
                    qualifierReader,
                    qualifierLength,
                    { offset },
                    { offset = it },
                    "set item"
                )
                val key = valueDefinition.readStorageBytes(setItemLength) { qualifierReader(offset++) }

                val setItemReference = setDefinition.itemRef(key, setReference)

                readValueFromStorage(Value, setItemReference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(setItemReference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else {
                        addChangeToOutput(version, SET_ADD, setItemReference)
                    }
                }
            }
        }
        MAP -> {
            @Suppress("UNCHECKED_CAST")
            val mapDefinition = definition as IsMapDefinition<Any, Any, IsPropertyContext>
            val mapReference = reference as CanContainMapItemReference<*, *, *>

            if (isAtEnd) {
                readValueFromStorage(MapSize, mapReference) { version, value ->
                    if (value == null) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(version, CHANGE, ReferenceNullPair(reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                    } else if (value == 0) {
                        addChangeToOutput.addStructure(version, reference, emptyMap<Any, Any>())
                    }
                }
            } else {
                // Read set contents. It is always a simple value for set since it is in the qualifier.
                val keyDefinition = (definition as IsMapDefinition<*, *, *>).keyDefinition
                val valueDefinition = (definition as IsMapDefinition<*, *, *>).valueDefinition
                val keySize = readQualifierLength(
                    qualifierReader,
                    qualifierLength,
                    { offset },
                    { offset = it },
                    "map key"
                )
                val key = keyDefinition.readStorageBytes(keySize) { qualifierReader(offset++) }

                val valueReference = mapDefinition.valueRef(key, mapReference)

                if (qualifierLength <= offset) {
                    readValueFromStorage(Value, valueReference) { version, value ->
                        if (value == null) {
                            @Suppress("UNCHECKED_CAST")
                            addChangeToOutput(version, CHANGE, ReferenceNullPair(valueReference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                        } else {
                            if (valueDefinition is IsEmbeddedValuesDefinition<*, *>) {
                                addChangeToOutput.addStructure(version, valueReference, Values(valueDefinition.dataModel, MutableValueItems()), embeddedPresence = true)
                            } else if (value !is TypedValue<*, *> && value !is MultiTypeEnum<*>) {
                                addChangeToOutput(version, CHANGE, valueReference with value)
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                readTypedValue(
                                    qualifierReader,
                                    qualifierLength,
                                    offset,
                                    readValueFromStorage,
                                    valueDefinition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                                    valueReference,
                                    select,
                                    addToCache,
                                    addChangeToOutput
                                )
                            }
                        }
                    }
                } else {
                    readComplexChanges(
                        qualifierReader,
                        qualifierLength,
                        offset,
                        valueDefinition,
                        mapDefinition.valueRef(key, mapReference),
                        select,
                        addToCache,
                        addChangeToOutput,
                        readValueFromStorage
                    )
                }
            }
        }
        ReferenceType.TYPE -> {
            @Suppress("UNCHECKED_CAST")
            val typedDefinition =
                definition as? IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>
                    ?: throw TypeException("Definition($index) $definition should be a TypedDefinition")

            typedDefinition.readComplexTypedValue(
                qualifierReader,
                qualifierLength,
                offset,
                readValueFromStorage,
                index,
                reference,
                select,
                addToCache,
                addChangeToOutput
            )
        }
    }
}

private fun <DM : IsValuesDataModel> readComplexChanges(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    definition: IsSubDefinition<*, *>,
    parentReference: IsPropertyReference<*, *, *>,
    select: IsPropRefGraph<DM>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader
) {
    when (definition) {
        is IsMultiTypeDefinition<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            readTypedValue(
                qualifierReader,
                qualifierLength,
                offset,
                readValueFromStorage,
                definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                parentReference,
                select,
                addToCache,
                addChangeToOutput
            )
        }
        is IsEmbeddedDefinition<*> -> {
            readEmbeddedValues(
                qualifierReader,
                qualifierLength,
                offset,
                readValueFromStorage,
                definition,
                select,
                parentReference,
                addToCache,
                addChangeToOutput
            )
        }
        is IsListDefinition<*, *>, is IsSetDefinition<*, *>, is IsMapDefinition<*, *, *> -> {
            readQualifierOfType(
                qualifierReader = qualifierReader,
                qualifierLength = qualifierLength,
                currentOffset = offset + 1,
                definition = definition,
                refStoreType = when(definition) {
                    is IsListDefinition<*, *> -> LIST
                    is IsSetDefinition<*, *> -> SET
                    is IsMapDefinition<*, *, *> -> MAP
                    else -> throw IllegalStateException("Unexpected definition type")
                },
                index = 0u, // Is ignored by addValueToOutput
                select = select,
                reference = parentReference,
                addChangeToOutput = addChangeToOutput,
                readValueFromStorage = readValueFromStorage,
                addToCache = addToCache
            )
        }
        else -> throw StorageException("Can only use Embedded as values with deeper values. Not $definition")
    }
}

/** Read a typed value */
private fun readTypedValue(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueWithVersionReader,
    valueDefinition: IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
    reference: IsPropertyReference<*, *, *>,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder
) {
    var qIndex1 = offset
    if (qualifierLength <= qIndex1) {
        readValueFromStorage(Value, reference) { version, value ->
            when (value) {
                null ->
                    @Suppress("UNCHECKED_CAST")
                    addChangeToOutput(version, CHANGE, ReferenceNullPair(reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>))
                is MultiTypeEnum<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    addChangeToOutput(
                        version, TYPE,
                        ReferenceTypePair(
                            reference as TypedPropertyReference<TypedValue<TypeEnum<Any>, Any>>,
                            value
                        )
                    )
                }
                is TypedValue<TypeEnum<Any>, Any> -> {
                    @Suppress("UNCHECKED_CAST")
                    addChangeToOutput(
                        version,
                        CHANGE,
                        ReferenceValuePair(
                            reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                            value
                        )
                    )
                }
                else -> throw TypeException("Unexpected stored value for TypedValue.")
            }
        }
    } else {
        initUIntByVarWithExtraInfo({ qualifierReader(qIndex1++) }) { typeIndex, _ ->
            valueDefinition.readComplexTypedValue(
                qualifierReader,
                qualifierLength,
                qIndex1,
                readValueFromStorage,
                typeIndex,
                reference,
                select,
                addToCache,
                addChangeToOutput
            )
        }
    }
}

/** Read a complex Typed value from [qualifierReader] */
private fun IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>.readComplexTypedValue(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueWithVersionReader,
    index: UInt,
    reference: IsPropertyReference<*, *, *>?,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder
) {
    val definition = this.definition(index)
        ?: throw DefNotFoundException("Unknown type $index for $this")
    val type = this.typeEnum.resolve(index)
        ?: throw DefNotFoundException("Unknown type $index for $this")
    val typedValueReference = TypedValueReference(type, this, reference as CanHaveComplexChildReference<*, *, *, *>?)

    if (qualifierLength <= offset && definition is IsEmbeddedValuesDefinition<*, *>) {
        readValueFromStorage(Value, typedValueReference) { version, value ->
            if (value != null) {
                addChangeToOutput.addStructure(version, typedValueReference, Values(definition.dataModel, MutableValueItems()), embeddedPresence = true)
            }
        }
        return
    }

    readComplexChanges(
        qualifierReader,
        qualifierLength,
        offset,
        definition,
        typedValueReference,
        select,
        addToCache,
        addChangeToOutput,
        readValueFromStorage
    )
}

/** Presence cells carry no defaults, but are required to reconstruct an empty structure. */
@Suppress("UNCHECKED_CAST")
private fun ChangeAdder.addStructure(version: ULong, reference: IsPropertyReference<*, *, *>, value: Any, embeddedPresence: Boolean = false) {
    this(version, if (embeddedPresence) EMBED_EXISTS else CHANGE, ReferenceValuePair(
        reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>, value,
    ))
}

private fun <DM : IsValuesDataModel> readEmbeddedValues(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueWithVersionReader,
    definition: IsEmbeddedDefinition<*>,
    select: IsPropRefGraph<DM>?,
    parentReference: IsPropertyReference<*, *, *>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder
) {
    val dataModel = definition.dataModel

    // If select is Graph then resolve sub graph.
    // Otherwise, it is null or is property itself so needs to be completely selected thus set as null.
    val specificSelect = if (select is IsPropRefGraph<*>) {
        @Suppress("UNCHECKED_CAST")
        select as IsPropRefGraph<IsValuesDataModel>
    } else null

    addToCache(offset - 1) { qr, l ->
        dataModel.readQualifier(
            qr, l,
            offset,
            specificSelect,
            parentReference,
            addChangeToOutput,
            readValueFromStorage,
            addToCache
        )
    }

    dataModel.readQualifier(
        qualifierReader,
        qualifierLength,
        offset,
        specificSelect,
        parentReference,
        addChangeToOutput,
        readValueFromStorage,
        addToCache
    )
}
