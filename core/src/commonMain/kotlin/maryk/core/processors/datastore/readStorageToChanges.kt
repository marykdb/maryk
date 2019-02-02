package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsDataModelWithValues
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.ChangeType.CHANGE
import maryk.core.processors.datastore.ChangeType.OBJECT_DELETE
import maryk.core.processors.datastore.ChangeType.SET_ADD
import maryk.core.processors.datastore.ChangeType.TYPE
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
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
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.AnyIndexedEnum
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyValuePropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.TypeReference
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

typealias ValueWithVersionReader = (StorageTypeEnum<IsPropertyDefinition<Any>>, IsPropertyDefinition<Any>?, (ULong, Any?) -> Unit) -> Unit
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
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.readStorageToChanges(
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
    when(changeType) {
        ChangeType.OBJECT_DELETE -> this.find { it is ObjectSoftDeleteChange }
        ChangeType.CHANGE -> this.find { it is Change }?.also { ((it as Change).referenceValuePairs as MutableList<ReferenceValuePair<*>>).add(changePart as ReferenceValuePair<*>) }
        ChangeType.DELETE -> this.find { it is Delete }?.also { ((it as Delete).references as MutableList<AnyValuePropertyReference>).add(changePart as AnyValuePropertyReference) }
        ChangeType.TYPE -> this.find { it is MultiTypeChange }?.also { ((it as MultiTypeChange).referenceTypePairs as MutableList<ReferenceTypePair<*>>).add(changePart as ReferenceTypePair<*>) }
        ChangeType.SET_ADD -> {
            this.find { it is SetChange }?.also { change ->
                val ref = changePart as SetItemReference<*, *>
                val setValueChanges = ((change as SetChange).setValueChanges as MutableList<SetValueChanges<*>>)
                setValueChanges.find { it.reference == ref.parentReference }?.also {
                    (it.addValues as MutableSet<Any>).add(ref.value)
                } ?: setValueChanges.add(
                    SetValueChanges(
                        ref.parentReference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *>,
                        addValues = mutableSetOf(ref.value),
                        deleteValues = null
                    )
                )
            }
        }
    } ?: this.add(createChange(changeType, changePart))
}

@Suppress("UNCHECKED_CAST")
private fun createChange(changeType: ChangeType, changePart: Any) = when(changeType) {
    ChangeType.OBJECT_DELETE -> ObjectSoftDeleteChange(changePart as Boolean)
    ChangeType.CHANGE -> Change(mutableListOf(changePart as ReferenceValuePair<Any>))
    ChangeType.DELETE -> Delete(mutableListOf(changePart as IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>, *>))
    ChangeType.TYPE -> MultiTypeChange(mutableListOf(changePart as ReferenceTypePair<*>))
    ChangeType.SET_ADD-> {
        val ref = changePart as SetItemReference<*, *>
        SetChange(mutableListOf(
            SetValueChanges(
                ref.parentReference as IsPropertyReference<Set<Any>, IsPropertyDefinition<Set<Any>>, *>,
                addValues = mutableSetOf(ref.value)
            )
        ))
    }
}

/**
 * Read specific [qualifier] from [offset].
 * [addChangeToOutput] is used to add changes to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache so it does not need to reprocess qualifier from start
 */
private fun <P: PropertyDefinitions> IsDataModel<P>.readQualifier(
    qualifier: ByteArray,
    offset: Int,
    select: IsPropRefGraph<P>?,
    parentReference: IsPropertyReference<*, *, *>?,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    addToCache: CacheProcessor
) {
    var qIndex = offset

    initIntByVarWithExtraInfo({ qualifier[qIndex++] }) { index, type ->
        val subSelect = select?.selectNodeOrNull(index)

        if (select != null && subSelect == null) {
            // Return null if not selected within select
            null
        } else {
            val isAtEnd = qualifier.size <= qIndex
            when (referenceStorageTypeOf(type)) {
                SPECIAL -> when (val specialType = completeReferenceTypeOf(qualifier[offset])) {
                    DELETE -> {
                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(ObjectDelete as StorageTypeEnum<IsPropertyDefinition<Any>>, objectDeletePropertyDefinition as IsPropertyDefinition<Any>) { version, value ->
                            if (value != null) {
                                addChangeToOutput(version, OBJECT_DELETE, value)
                            }
                        }
                    }
                    MAP_KEY -> throw Exception("Cannot handle Special type $specialType in qualifier")
                    else -> throw Exception("Not recognized special type $specialType")
                }
                VALUE -> {
                    val definition = this.properties[index]
                        ?: throw Exception("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(
                            Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                            definition
                        ) { version, value ->
                            val ref =
                                definition.getRef(parentReference) as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
                            if (value == null) {
                                addChangeToOutput(version, ChangeType.DELETE, ref)
                            } else {
                                if (value !is TypedValue<*, *> || value.value != Unit) {
                                    addChangeToOutput(version, CHANGE, ReferenceValuePair(ref, value))
                                } else { // Is a TypedValue with Unit as value
                                    @Suppress("UNCHECKED_CAST")
                                    readTypedValue(ref, qualifier, qIndex, readValueFromStorage, definition as IsMultiTypeDefinition<AnyIndexedEnum, IsPropertyContext>, addChangeToOutput, select, addToCache)
                                }
                            }
                        }
                    } else { // Is Complex value
                        val reference = definition.getRef(parentReference)
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
                        ?: throw Exception("No definition for $index in $this at $index")

                    val reference = definition.getRef(parentReference)

                    if (isAtEnd) {
                        // Handle embed deletes
                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(
                            Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                            definition
                        ) { version, value ->
                            val ref =
                                definition.getRef(parentReference) as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
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
                LIST -> if (isAtEnd) {
                    // Used for the list size. Is Ignored for changes
                } else {
                    val definition = this.properties[index]!!
                    @Suppress("UNCHECKED_CAST")
                    val listDefinition = definition as IsListDefinition<Any, IsPropertyContext>
                    @Suppress("UNCHECKED_CAST")
                    val reference = definition.getRef(parentReference) as ListReference<Any, IsPropertyContext>

                    // Read set contents. Always a simple value for set since it is in qualifier
                    val valueDefinition = ((definition as IsListDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                    var listItemIndex = qIndex

                    val listIndex = initUInt(reader = { qualifier[listItemIndex++] })

                    @Suppress("UNCHECKED_CAST")
                    readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, valueDefinition as IsPropertyDefinition<Any>) { version, value ->
                        if (value == null) {
                            addChangeToOutput(version, ChangeType.DELETE, listDefinition.getItemRef(listIndex, reference))
                        } else {
                            addChangeToOutput(version, CHANGE, listDefinition.getItemRef(listIndex, reference) with value)
                        }
                    }
                }
                SET -> if (isAtEnd) {
                    // Used for the set size. Is Ignored for changes
                } else {
                    val definition = this.properties[index]!!
                    @Suppress("UNCHECKED_CAST")
                    val setDefinition = definition as IsSetDefinition<Any, IsPropertyContext>
                    @Suppress("UNCHECKED_CAST")
                    val reference = definition.getRef(parentReference) as SetReference<Any, IsPropertyContext>

                    // Read set contents. Always a simple value for set since it is in qualifier
                    val valueDefinition = ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                    var setItemIndex = qIndex

                    val key = valueDefinition.readStorageBytes(qualifier.size - qIndex) { qualifier[setItemIndex++] }

                    @Suppress("UNCHECKED_CAST")
                    readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, valueDefinition as IsPropertyDefinition<Any>) { version, value ->
                        if (value == null) {
                            addChangeToOutput(version, ChangeType.DELETE, setDefinition.getItemRef(key, reference))
                        } else {
                            addChangeToOutput(version, SET_ADD, setDefinition.getItemRef(key, reference))
                        }
                    }
                }
                MAP -> {
                    val definition = this.properties[index]!!
                    @Suppress("UNCHECKED_CAST")
                    val mapDefinition = definition as IsMapDefinition<Any, Any, IsPropertyContext>
                    @Suppress("UNCHECKED_CAST")
                    val reference = definition.getRef(parentReference) as MapReference<Any, Any, IsPropertyContext>

                    if (isAtEnd) {
                        // Used for the map size. Is Ignored for changes
                    } else {
                        // Read set contents. Always a simple value for set since it is in qualifier
                        val keyDefinition = ((definition as IsMapDefinition<*, *, *>).keyDefinition as IsSimpleValueDefinition<*, *>)
                        val valueDefinition = ((definition as IsMapDefinition<*, *, *>).valueDefinition as IsSubDefinition<*, *>)
                        var mapItemIndex = qIndex

                        val keySize = initIntByVar { qualifier[mapItemIndex++] }
                        val key = keyDefinition.readStorageBytes(keySize) { qualifier[mapItemIndex++] }

                        if (qualifier.size <= mapItemIndex) {
                            @Suppress("UNCHECKED_CAST")
                            readValueFromStorage(
                                Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                                valueDefinition as IsPropertyDefinition<Any>
                            ) { version, value ->
                                if (value == null) {
                                    addChangeToOutput(version, ChangeType.DELETE, mapDefinition.getValueRef(key, reference))
                                } else {
                                    addChangeToOutput(version, CHANGE, mapDefinition.getValueRef(key, reference) with value)
                                }
                            }
                        } else {
                            readComplexChanges(
                                qualifier,
                                mapItemIndex,
                                valueDefinition,
                                mapDefinition.getValueRef(key, reference),
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
                        ?: throw Exception("No definition for $index in $this at $index")
                    @Suppress("UNCHECKED_CAST")
                    val typedDefinition = definition.definition as? IsMultiTypeDefinition<AnyIndexedEnum, IsPropertyContext>
                        ?: throw Exception("Definition($index) ${definition.definition} should be a TypedDefinition")

                    typedDefinition.readComplexTypedValue(parentReference, index.toUInt(), addChangeToOutput, qualifier, qIndex, readValueFromStorage, select, addToCache)
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
                parentReference, qualifier, offset, readValueFromStorage, definition as IsMultiTypeDefinition<AnyIndexedEnum, IsPropertyContext>, addChangeToOutput, select, addToCache
            )
        }
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition,
                parentReference,
                select,
                addToCache,
                offset,
                addChangeToOutput,
                readValueFromStorage,
                qualifier
            )
        }
        else -> throw Exception("Can only use Embedded as values with deeper values $definition")
    }
}

/** Read a typed value */
private fun readTypedValue(
    reference: IsPropertyReference<*, *, *>?,
    qualifier: ByteArray,
    offset: Int,
    readValueFromStorage: ValueWithVersionReader,
    valueDefinition: IsMultiTypeDefinition<AnyIndexedEnum, IsPropertyContext>,
    changeAdder: ChangeAdder,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor
) {
    var qIndex1 = offset
    if (qualifier.size <= qIndex1) {
        @Suppress("UNCHECKED_CAST")
        readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, valueDefinition as IsPropertyDefinition<Any>) { version, value ->
            if (value == null) {
                changeAdder(version, ChangeType.DELETE, reference as Any)
            } else {
                if (value is TypedValue<*, *>) {
                    if (value.value == Unit) {
                        changeAdder(
                            version, TYPE,
                            ReferenceTypePair(
                                reference as MultiTypePropertyReference<AnyIndexedEnum, *, MultiTypeDefinitionWrapper<AnyIndexedEnum, *, *, *>, *>,
                                value.type as AnyIndexedEnum
                            )
                        )
                    } else {
                        changeAdder(
                            version,
                            CHANGE,
                            ReferenceValuePair(
                                reference as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                                value
                            )
                        )
                    }
                } else {
                    throw Exception("Unexpected stored value for TypedValue.")
                }
            }
        }
    } else {
        initUIntByVarWithExtraInfo({ qualifier[qIndex1++] }) { typeIndex, _ ->
            valueDefinition.readComplexTypedValue(
                reference,
                typeIndex,
                changeAdder,
                qualifier,
                qIndex1,
                readValueFromStorage,
                select,
                addToCache
            )
        }
    }
}

/** Read a complex Typed value from qualifier */
private fun <E: IndexedEnum<E>> IsMultiTypeDefinition<E, IsPropertyContext>.readComplexTypedValue(
    reference: IsPropertyReference<*, *, *>?,
    index: UInt,
    addChangeToOutput: ChangeAdder,
    qualifier: ByteArray,
    qIndex: Int,
    readValueFromStorage: ValueWithVersionReader,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor
) {
    @Suppress("UNCHECKED_CAST")
    val definition = this.definition(index)
    @Suppress("UNCHECKED_CAST")
    val type = this.type(index) ?: throw Exception("Unknown type $index for $this")
    val typedReference = TypeReference(type, this, reference as CanHaveComplexChildReference<*, *, *, *>?)

    if (qualifier.size <= qIndex) {
        return // Skip because is only complex exists indicator
    }

    when (definition) {
        is IsEmbeddedDefinition<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            readEmbeddedValues(
                definition,
                typedReference,
                select,
                addToCache,
                qIndex,
                addChangeToOutput,
                readValueFromStorage,
                qualifier
            )
        }
        else -> throw Exception("Can only use Embedded/MultiType as complex value type in Multi Type $definition")
    }
}

private fun <P : PropertyDefinitions> readEmbeddedValues(
    definition: IsEmbeddedDefinition<*, *>,
    parentReference: IsPropertyReference<*, *, *>?,
    select: IsPropRefGraph<P>?,
    addToCache: CacheProcessor,
    index: Int,
    addChangeToOutput: ChangeAdder,
    readValueFromStorage: ValueWithVersionReader,
    qualifier: ByteArray
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

    addToCache(index - 1) { q ->
        dataModel.readQualifier(
            q,
            index,
            specificSelect,
            parentReference,
            addChangeToOutput,
            readValueFromStorage,
            addToCache
        )
    }

    dataModel.readQualifier(
        qualifier,
        index,
        specificSelect,
        parentReference,
        addChangeToOutput,
        readValueFromStorage,
        addToCache
    )
}
