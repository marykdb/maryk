package maryk.core.processors.datastore

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.StorageException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.processors.datastore.ChangeType.CHANGE
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
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
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
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SetValueChanges
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.exceptions.ParseException

typealias ValueWithVersionReader = (StorageTypeEnum<IsPropertyDefinition<out Any>>, IsPropertyReferenceForCache<*, *>, (ULong, Any?) -> Unit) -> Unit
private typealias ChangeAdder = (ULong, ChangeType, Any) -> Unit

private enum class ChangeType {
    OBJECT_CREATE, OBJECT_DELETE, CHANGE, DELETE, SET_ADD, TYPE
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
): List<VersionedChanges> {
    // Used to collect all found ValueItems
    val mutableVersionedChanges = mutableListOf<VersionedChanges>()

    // Add changes to versionedChangesCollection
    val changeAdder: ChangeAdder = { version: ULong, changeType: ChangeType, changePart: Any ->
        val index = mutableVersionedChanges.binarySearch { it.version compareTo version }

        if (index < 0) {
            mutableVersionedChanges.add(
                (index * -1) - 1,
                VersionedChanges(version, mutableListOf(createChange(changeType, changePart)))
            )
        } else {
            (mutableVersionedChanges[index].changes as MutableList<IsChange>).addChange(changeType, changePart)
        }
    }

    if (creationVersion != null) {
        changeAdder(creationVersion, OBJECT_CREATE, Unit)
    }

    processQualifiers(getQualifier) { qualifierReader, qualifierLength, addToCache ->
        // Otherwise, try to get a new qualifier processor from DataModel
        this.readQualifier(qualifierReader, qualifierLength, 0, select, null, changeAdder, processValue, addToCache)
    }

    // Create Values
    return mutableVersionedChanges
}

/** Adds change to existing list or creates a new change*/
private fun MutableList<IsChange>.addChange(changeType: ChangeType, changePart: Any) {
    @Suppress("UNCHECKED_CAST")
    when (changeType) {
        OBJECT_CREATE -> this.find { it is ObjectCreate }
        OBJECT_DELETE -> this.find { it is ObjectSoftDeleteChange }
        CHANGE -> this.find { it is Change }?.also {
            ((it as Change).referenceValuePairs as MutableList<ReferenceValuePair<*>>).add(
                changePart as ReferenceValuePair<*>
            )
        }
        ChangeType.DELETE -> this.find { it is Delete }?.also {
            val reference = changePart as AnyValuePropertyReference
            val toDelete = ((it as Delete).references as MutableList<AnyValuePropertyReference>)
            if (reference is IsPropertyReferenceWithParent<*, *, *, *>) {
                var toSearch: AnyPropertyReference? = reference
                while (toSearch is IsPropertyReferenceWithParent<*, *, *, *>) {
                    if (toDelete.contains(toSearch as AnyValuePropertyReference)) {
                        return@also
                    }

                    toSearch = toSearch.parentReference ?: break
                }
            }
            toDelete.add(reference)
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
    CHANGE -> Change(mutableListOf(changePart as ReferenceValuePair<Any>))
    ChangeType.DELETE -> Delete(mutableListOf(changePart as IsPropertyReference<*, IsValueDefinitionWrapper<*, *, IsPropertyContext, *>, *>))
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
    parentReference: IsPropertyReference<*, *, *>?,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var currentOffset = offset

    initUIntByVarWithExtraInfo({ qualifierReader(currentOffset++) }) { index, type ->
        val subSelect = select?.selectNodeOrNull(index)

        if (select != null && subSelect == null) {
            // Return null if not selected within select
            null
        } else {
            when (val refStoreType = referenceStorageTypeOf(type)) {
                DELETE -> {
                    readValueFromStorage(ObjectDelete, ObjectDeleteReference) { version, value ->
                        if (value != null) {
                            addChangeToOutput(version, OBJECT_DELETE, value)
                        }
                    }
                }
                else -> {
                    val definition = this[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")
                    readQualifierOfType(
                        qualifierReader,
                        qualifierLength,
                        currentOffset,
                        definition.definition,
                        refStoreType,
                        index,
                        select,
                        definition.ref(parentReference),
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
                        addChangeToOutput(version, ChangeType.DELETE, ref)
                    } else {
                        if (value !is TypedValue<*, *>) {
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
                        addChangeToOutput(version, ChangeType.DELETE, reference as Any)
                    } // Else this value just exists
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
                        addChangeToOutput(version, ChangeType.DELETE, reference as Any)
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val listDefinition = definition as IsListDefinition<Any, IsPropertyContext>
                val listReference = reference as CanContainListItemReference<*, *, *>

                val listIndex = initUInt({ qualifierReader(offset++) })

                val itemReference = listDefinition.itemRef(listIndex, listReference)

                readValueFromStorage(Value, itemReference) { version, value ->
                    if (value == null) {
                        addChangeToOutput(
                            version,
                            ChangeType.DELETE,
                            itemReference
                        )
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
                        addChangeToOutput(version, ChangeType.DELETE, reference as Any)
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val setDefinition = definition as IsSetDefinition<Any, IsPropertyContext>
                val setReference = reference as CanContainSetItemReference<*, *, *>

                // Read set contents. It is always a simple value for set since it is in the qualifier.
                val valueDefinition =
                    ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                val setItemLength = initIntByVar { qualifierReader(offset++) }
                val key = valueDefinition.readStorageBytes(setItemLength) { qualifierReader(offset++) }

                val setItemReference = setDefinition.itemRef(key, setReference)

                readValueFromStorage(Value, setItemReference) { version, value ->
                    if (value == null) {
                        addChangeToOutput(
                            version,
                            ChangeType.DELETE,
                            setItemReference
                        )
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
                        addChangeToOutput(version, ChangeType.DELETE, reference as Any)
                    }
                }
            } else {
                // Read set contents. It is always a simple value for set since it is in the qualifier.
                val keyDefinition =
                    ((definition as IsMapDefinition<*, *, *>).keyDefinition as IsSimpleValueDefinition<*, *>)
                val valueDefinition =
                    ((definition as IsMapDefinition<*, *, *>).valueDefinition as IsSubDefinition<*, *>)
                val keySize = initIntByVar { qualifierReader(offset++) }
                val key = keyDefinition.readStorageBytes(keySize) { qualifierReader(offset++) }

                val valueReference = mapDefinition.valueRef(key, mapReference)

                if (qualifierLength <= offset) {
                    readValueFromStorage(Value, valueReference) { version, value ->
                        if (value == null) {
                            addChangeToOutput(version, ChangeType.DELETE, valueReference)
                        } else {
                            if (value !is TypedValue<*, *>) {
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
        is IsListDefinition<*, *> -> {
            readQualifierOfType(
                qualifierReader = qualifierReader,
                qualifierLength = qualifierLength,
                currentOffset = offset + 1,
                definition = definition,
                refStoreType = LIST,
                index = 0u, // Is ignored by addValueToOutput
                select = select,
                reference = parentReference,
                addChangeToOutput = addChangeToOutput,
                readValueFromStorage = readValueFromStorage,
                addToCache = addToCache
            )
        }
        is IsSetDefinition<*, *> -> {
            readQualifierOfType(
                qualifierReader = qualifierReader,
                qualifierLength = qualifierLength,
                currentOffset = offset + 1,
                definition = definition,
                refStoreType = SET,
                index = 0u, // Is ignored by addValueToOutput
                select = select,
                reference = parentReference,
                addChangeToOutput = addChangeToOutput,
                readValueFromStorage = readValueFromStorage,
                addToCache = addToCache
            )
        }
        is IsMapDefinition<*, *, *> -> {
            readQualifierOfType(
                qualifierReader = qualifierReader,
                qualifierLength = qualifierLength,
                currentOffset = offset + 1,
                definition = definition,
                refStoreType = MAP,
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
            if (value == null) {
                addChangeToOutput(version, ChangeType.DELETE, reference as Any)
            } else {
                if (value is TypedValue<TypeEnum<Any>, Any>) {
                    if (value.value == Unit) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(
                            version, TYPE,
                            ReferenceTypePair(
                                reference as TypedPropertyReference<TypedValue<TypeEnum<Any>, Any>>,
                                value.type
                            )
                        )
                    } else {
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
                } else {
                    throw TypeException("Unexpected stored value for TypedValue.")
                }
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

    if (qualifierLength <= offset) {
        return // Skip because is only complex exists indicator
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
