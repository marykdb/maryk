package maryk.core.processors.datastore

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.StorageException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsDataModelWithValues
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.ChangeType.CHANGE
import maryk.core.processors.datastore.ChangeType.OBJECT_DELETE
import maryk.core.processors.datastore.ChangeType.SET_ADD
import maryk.core.processors.datastore.ChangeType.TYPE
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsAnyEmbeddedDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.AnyValuePropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.completeReferenceTypeOf
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SetValueChanges
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.query.pairs.ReferenceValuePair

typealias ValueWithVersionReader = (StorageTypeEnum<IsPropertyDefinition<out Any>>, IsPropertyDefinition<out Any>?, (ULong, Any?) -> Unit) -> Unit
private typealias ChangeAdder = (ULong, ChangeType, Any) -> Unit

private enum class ChangeType {
    OBJECT_DELETE, CHANGE, DELETE, SET_ADD, TYPE
}

private val objectDeletePropertyDefinition = BooleanDefinition()

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.readStorageToChanges(
    getQualifier: () -> ByteArray?,
    select: RootPropRefGraph<P>?,
    processValue: ValueWithVersionReader
): List<VersionedChanges> {
    // Used to collect all found ValueItems
    val mutableVersionedChanges = mutableListOf<VersionedChanges>()

    // Adds changes to versionedChangesCollection
    val changeAdder: ChangeAdder = { version: ULong, changeType: ChangeType, changePart: Any ->
        val index = mutableVersionedChanges.binarySearch { it.version.compareTo(version) }

        if (index < 0) {
            mutableVersionedChanges.add(
                (index * -1) - 1,
                VersionedChanges(version, mutableListOf(createChange(changeType, changePart)))
            )
        } else {
            (mutableVersionedChanges[index].changes as MutableList<IsChange>).addChange(changeType, changePart)
        }
    }

    processQualifiers(getQualifier) { qualifier, addToCache ->
        // Otherwise try to get a new qualifier processor from DataModel
        (this as IsDataModel<P>).readQualifier(qualifier, 0, select, null, changeAdder, processValue, addToCache)
    }

    // Create Values
    return mutableVersionedChanges
}

/** Adds change to existing list or creates a new change*/
private fun MutableList<IsChange>.addChange(changeType: ChangeType, changePart: Any) {
    @Suppress("UNCHECKED_CAST")
    when (changeType) {
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
                setValueChanges.find { it.reference == ref.parentReference }?.also {
                    (it.addValues as MutableSet<Any>).add(ref.value)
                } ?: setValueChanges.add(
                    SetValueChanges(
                        ref.parentReference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *>,
                        addValues = mutableSetOf(ref.value)
                    )
                )
            }
        }
    } ?: this.add(createChange(changeType, changePart))
}

@Suppress("UNCHECKED_CAST")
private fun createChange(changeType: ChangeType, changePart: Any) = when (changeType) {
    OBJECT_DELETE -> ObjectSoftDeleteChange(changePart as Boolean)
    CHANGE -> Change(mutableListOf(changePart as ReferenceValuePair<Any>))
    ChangeType.DELETE -> Delete(mutableListOf(changePart as IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>, *>))
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
 * Read specific [qualifier] from [offset].
 * [addChangeToOutput] is used to add changes to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache so it does not need to reprocess qualifier from start
 */
private fun <P : PropertyDefinitions> IsDataModel<P>.readQualifier(
    qualifier: ByteArray,
    offset: Int,
    select: IsPropRefGraph<P>?,
    parentReference: IsPropertyReference<*, *, *>?,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var qIndex = offset

    initUIntByVarWithExtraInfo({ qualifier[qIndex++] }) { index, type ->
        val subSelect = select?.selectNodeOrNull(index)

        if (select != null && subSelect == null) {
            // Return null if not selected within select
            null
        } else {
            val isAtEnd = qualifier.size <= qIndex
            when (referenceStorageTypeOf(type)) {
                SPECIAL -> when (val specialType = completeReferenceTypeOf(qualifier[offset])) {
                    DELETE -> {
                        readValueFromStorage(ObjectDelete, objectDeletePropertyDefinition) { version, value ->
                            if (value != null) {
                                addChangeToOutput(version, OBJECT_DELETE, value)
                            }
                        }
                    }
                    MAP_KEY -> throw TypeException("Cannot handle Special type $specialType in qualifier")
                    else -> throw TypeException("Not recognized special type $specialType")
                }
                VALUE -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        readValueFromStorage(Value, definition) { version, value ->
                            @Suppress("UNCHECKED_CAST")
                            val ref =
                                definition.ref(parentReference) as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, ref)
                            } else {
                                if (value !is TypedValue<*, *>) {
                                    addChangeToOutput(version, CHANGE, ReferenceValuePair(ref, value))
                                } else { // Is a TypedValue with Unit as value
                                    @Suppress("UNCHECKED_CAST")
                                    readTypedValue(
                                        ref,
                                        qualifier,
                                        qIndex,
                                        readValueFromStorage,
                                        definition as IsMultiTypeDefinition<IndexedEnum, IsPropertyContext>,
                                        select,
                                        addToCache,
                                        addChangeToOutput
                                    )
                                }
                            }
                        }
                    } else { // Is Complex value
                        val reference = definition.ref(parentReference)
                        readComplexChanges(
                            qualifier,
                            qIndex,
                            definition,
                            reference,
                            select,
                            addToCache,
                            addChangeToOutput,
                            readValueFromStorage
                        )
                    }
                }
                EMBED -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    val reference = definition.ref(parentReference)

                    if (isAtEnd) {
                        // Handle embed deletes
                        readValueFromStorage(Value, definition) { version, value ->
                            val ref =
                                definition.ref(parentReference)
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, ref)
                            } // Else this value just exists
                        }
                    } else {
                        readComplexChanges(
                            qualifier,
                            qIndex,
                            definition,
                            reference,
                            select,
                            addToCache,
                            addChangeToOutput,
                            readValueFromStorage
                        )
                    }
                }
                LIST -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        readValueFromStorage(ListSize, definition) { version, value ->
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, definition.ref(parentReference))
                            }
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val listDefinition = definition as IsListDefinition<Any, IsPropertyContext>
                        @Suppress("UNCHECKED_CAST")
                        val reference = definition.ref(parentReference) as ListReference<Any, IsPropertyContext>

                        // Read set contents. Always a simple value for set since it is in qualifier
                        val valueDefinition =
                            ((definition as IsListDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                        var listItemIndex = qIndex

                        val listIndex = initUInt(reader = { qualifier[listItemIndex++] })

                        readValueFromStorage(Value, valueDefinition) { version, value ->
                            if (value == null) {
                                addChangeToOutput(
                                    version,
                                    ChangeType.DELETE,
                                    listDefinition.itemRef(listIndex, reference)
                                )
                            } else {
                                addChangeToOutput(
                                    version,
                                    CHANGE,
                                    listDefinition.itemRef(listIndex, reference) with value
                                )
                            }
                        }
                    }
                }
                SET -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        readValueFromStorage(SetSize, definition) { version, value ->
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, definition.ref(parentReference))
                            }
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val setDefinition = definition as IsSetDefinition<Any, IsPropertyContext>
                        @Suppress("UNCHECKED_CAST")
                        val reference = definition.ref(parentReference) as SetReference<Any, IsPropertyContext>

                        // Read set contents. Always a simple value for set since it is in qualifier
                        val valueDefinition =
                            ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                        var setItemIndex = qIndex

                        val key =
                            valueDefinition.readStorageBytes(qualifier.size - qIndex) { qualifier[setItemIndex++] }

                        readValueFromStorage(Value, valueDefinition) { version, value ->
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, setDefinition.itemRef(key, reference))
                            } else {
                                addChangeToOutput(version, SET_ADD, setDefinition.itemRef(key, reference))
                            }
                        }
                    }
                }
                MAP -> {
                    val definition = this.properties[index]!!
                    @Suppress("UNCHECKED_CAST")
                    val mapDefinition = definition as IsMapDefinition<Any, Any, IsPropertyContext>
                    @Suppress("UNCHECKED_CAST")
                    val reference = definition.ref(parentReference) as MapReference<Any, Any, IsPropertyContext>

                    if (isAtEnd) {
                        readValueFromStorage(MapSize, definition) { version, value ->
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, definition.ref(parentReference))
                            }
                        }
                    } else {
                        // Read set contents. Always a simple value for set since it is in qualifier
                        val keyDefinition =
                            ((definition as IsMapDefinition<*, *, *>).keyDefinition as IsSimpleValueDefinition<*, *>)
                        val valueDefinition =
                            ((definition as IsMapDefinition<*, *, *>).valueDefinition as IsSubDefinition<*, *>)
                        val keySize = initIntByVar { qualifier[qIndex++] }
                        val key = keyDefinition.readStorageBytes(keySize) { qualifier[qIndex++] }

                        if (qualifier.size <= qIndex) {
                            readValueFromStorage(Value, valueDefinition) { version, value ->
                                val valueReference = mapDefinition.valueRef(key, reference)
                                if (value == null) {
                                    addChangeToOutput(version, ChangeType.DELETE, valueReference)
                                } else {
                                    if (value !is TypedValue<*, *>) {
                                        addChangeToOutput(version, CHANGE, valueReference with value)
                                    } else {
                                        @Suppress("UNCHECKED_CAST")
                                        readTypedValue(
                                            valueReference,
                                            qualifier,
                                            qIndex,
                                            readValueFromStorage,
                                            valueDefinition as IsMultiTypeDefinition<IndexedEnum, IsPropertyContext>,
                                            select,
                                            addToCache,
                                            addChangeToOutput
                                        )
                                    }
                                }
                            }
                        } else {
                            readComplexChanges(
                                qualifier,
                                qIndex,
                                valueDefinition,
                                mapDefinition.valueRef(key, reference),
                                select,
                                addToCache,
                                addChangeToOutput,
                                readValueFromStorage
                            )
                        }
                    }
                }
                ReferenceType.TYPE -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")
                    @Suppress("UNCHECKED_CAST")
                    val typedDefinition =
                        definition.definition as? IsMultiTypeDefinition<IndexedEnum, IsPropertyContext>
                            ?: throw TypeException("Definition($index) ${definition.definition} should be a TypedDefinition")

                    typedDefinition.readComplexTypedValue(
                        parentReference,
                        index.toUInt(),
                        qualifier,
                        qIndex,
                        readValueFromStorage,
                        select,
                        addToCache,
                        addChangeToOutput
                    )
                }
            }
        }
    }
}

private fun <P : PropertyDefinitions> readComplexChanges(
    qualifier: ByteArray,
    offset: Int,
    definition: IsPropertyDefinition<*>,
    parentReference: IsPropertyReference<*, *, *>?,
    select: IsPropRefGraph<P>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader
) {
    when (definition) {
        is IsMultiTypeDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            readTypedValue(
                parentReference,
                qualifier,
                offset,
                readValueFromStorage,
                definition as IsMultiTypeDefinition<IndexedEnum, IsPropertyContext>,
                select,
                addToCache,
                addChangeToOutput
            )
        }
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition,
                parentReference,
                select,
                readValueFromStorage,
                addToCache,
                qualifier,
                offset,
                addChangeToOutput
            )
        }
        else -> throw StorageException("Can only use Embedded as values with deeper values $definition")
    }
}

/** Read a typed value */
private fun readTypedValue(
    reference: IsPropertyReference<*, *, *>?,
    qualifier: ByteArray,
    offset: Int,
    readValueFromStorage: ValueWithVersionReader,
    valueDefinition: IsMultiTypeDefinition<IndexedEnum, IsPropertyContext>,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder
) {
    var qIndex1 = offset
    if (qualifier.size <= qIndex1) {
        readValueFromStorage(Value, valueDefinition) { version, value ->
            if (value == null) {
                addChangeToOutput(version, ChangeType.DELETE, reference as Any)
            } else {
                if (value is TypedValue<IndexedEnum, Any>) {
                    if (value.value == Unit) {
                        @Suppress("UNCHECKED_CAST")
                        addChangeToOutput(
                            version, TYPE,
                            ReferenceTypePair(
                                reference as TypedPropertyReference<TypedValue<IndexedEnum, Any>>,
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
        initUIntByVarWithExtraInfo({ qualifier[qIndex1++] }) { typeIndex, _ ->
            valueDefinition.readComplexTypedValue(
                reference,
                typeIndex,
                qualifier,
                qIndex1,
                readValueFromStorage,
                select,
                addToCache,
                addChangeToOutput
            )
        }
    }
}

/** Read a complex Typed value from qualifier */
private fun <E : IndexedEnum> IsMultiTypeDefinition<E, IsPropertyContext>.readComplexTypedValue(
    reference: IsPropertyReference<*, *, *>?,
    index: UInt,
    qualifier: ByteArray,
    qIndex: Int,
    readValueFromStorage: ValueWithVersionReader,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addChangeToOutput: ChangeAdder
) {
    val definition = this.definition(index)
    val type = this.typeEnum.resolve(index)
        ?: throw DefNotFoundException("Unknown type $index for $this")
    val typedValueReference = TypedValueReference(type, this, reference as CanHaveComplexChildReference<*, *, *, *>?)

    if (qualifier.size <= qIndex) {
        return // Skip because is only complex exists indicator
    }

    when (definition) {
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition,
                typedValueReference,
                select,
                readValueFromStorage,
                addToCache,
                qualifier,
                qIndex,
                addChangeToOutput
            )
        }
        else -> throw StorageException("Can only use Embedded/MultiType as complex value type in Multi Type $definition")
    }
}

private fun <P : PropertyDefinitions> readEmbeddedValues(
    definition: IsEmbeddedDefinition<*, *>,
    parentReference: IsPropertyReference<*, *, *>?,
    select: IsPropRefGraph<P>?,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor,
    qualifier: ByteArray,
    offset: Int,
    addChangeToOutput: ChangeAdder
) {
    @Suppress("UNCHECKED_CAST")
    val dataModel =
        (definition as IsAnyEmbeddedDefinition).dataModel as IsDataModelWithValues<*, PropertyDefinitions, *>

    // If select is Graph then resolve sub graph.
    // Otherwise is null or is property itself so needs to be completely selected thus set as null.
    val specificSelect = if (select is IsPropRefGraph<*>) {
        @Suppress("UNCHECKED_CAST")
        select as IsPropRefGraph<PropertyDefinitions>
    } else null

    addToCache(offset - 1) { q ->
        dataModel.readQualifier(
            q,
            offset,
            specificSelect,
            parentReference,
            addChangeToOutput,
            readValueFromStorage,
            addToCache
        )
    }

    dataModel.readQualifier(
        qualifier,
        offset,
        specificSelect,
        parentReference,
        addChangeToOutput,
        readValueFromStorage,
        addToCache
    )
}
