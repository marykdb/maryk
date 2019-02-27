package maryk.core.processors.datastore

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.exceptions.StorageException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.models.IsDataModelWithValues
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.AnyPropertyDefinitionWrapper
import maryk.core.properties.enum.AnyIndexedEnum
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.completeReferenceTypeOf
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.properties.types.TypedValue
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

typealias ValueReader = (StorageTypeEnum<IsPropertyDefinition<Any>>, IsPropertyDefinition<Any>?) -> Any?
private typealias AddToValues = (Int, Any) -> Unit
private typealias AddValue = (Any) -> Unit

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.convertStorageToValues(
    getQualifier: () -> ByteArray?,
    select: RootPropRefGraph<P>?,
    processValue: ValueReader
): Values<DM, P> {
    // Used to collect all found ValueItems
    val mutableValuesItems = MutableValueItems()

    // Adds valueItems to collection
    val valueAdder: AddToValues = { index: Int, value: Any ->
        mutableValuesItems += ValueItem(index, value)
    }

    processQualifiers(getQualifier) { qualifier, addToCache ->
        // Otherwise try to get a new qualifier processor from DataModel
        (this as IsDataModel<P>).readQualifier(qualifier, 0, select, valueAdder, processValue, addToCache)
    }

    // Create Values
    return this.values(null) {
        mutableValuesItems
    }
}

/**
 * Read specific [qualifier] from [offset].
 * [addValueToOutput] is used to add values to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache so it does not need to reprocess qualifier from start
 */
private fun <P : PropertyDefinitions> IsDataModel<P>.readQualifier(
    qualifier: ByteArray,
    offset: Int,
    select: IsPropRefGraph<*>?,
    addValueToOutput: AddToValues,
    readValueFromStorage: ValueReader,
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
                    } // Ignore since it should be handled on higher level
                    MAP_KEY -> throw TypeException("Cannot handle Special type $specialType in qualifier")
                    else -> throw TypeException("Not recognized special type $specialType")
                }
                VALUE -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")
                    val valueAdder: AddValue = { addValueToOutput(index, it) }

                    if (isAtEnd) {
                        @Suppress("UNCHECKED_CAST")
                        val value =
                            readValueFromStorage(Embed as StorageTypeEnum<IsPropertyDefinition<Any>>, definition)
                        when (value) {
                            null -> // Ensure that next potential embedded values are not read because is deleted
                                addToCache(offset) {
                                    // Ignore reading and return
                                }
                            is TypedValue<*, *> -> readTypedValue(
                                qualifier = qualifier,
                                offset = qIndex,
                                readValueFromStorage = readValueFromStorage,
                                valueDefinition = definition as IsMultiTypeDefinition<*, *>,
                                select = select,
                                addToCache = addToCache,
                                addValueToOutput = valueAdder
                            )
                            else -> valueAdder(value)
                        }
                    } else {
                        readComplexValueFromStorage(
                            definition,
                            qualifier,
                            qIndex,
                            readValueFromStorage,
                            valueAdder,
                            select,
                            addToCache,
                            addValueToOutput,
                            index
                        )
                    }
                }
                EMBED -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")
                    val valueAdder: AddValue = { addValueToOutput(index, it) }

                    if (isAtEnd) {
                        @Suppress("UNCHECKED_CAST")
                        val embedValue =
                            readValueFromStorage(Embed as StorageTypeEnum<IsPropertyDefinition<Any>>, definition)
                        if (embedValue == null) {
                            // Ensure that next embedded values are not read
                            addToCache(offset) {
                                // Ignore reading and return
                            }
                        } else null // unknown value so ignore
                    } else {
                        readComplexValueFromStorage(
                            definition,
                            qualifier,
                            qIndex,
                            readValueFromStorage,
                            valueAdder,
                            select,
                            addToCache,
                            addValueToOutput,
                            index
                        )
                    }
                }
                LIST -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a list size
                        @Suppress("UNCHECKED_CAST")
                        val listSize = readValueFromStorage(
                            ListSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                            definition
                        ) as Int?

                        if (listSize != null) {
                            // If not null we can create an empty list of listSize
                            val list = ArrayList<Any>(listSize)
                            val listValueAdder: AddToValues = { i, value -> list.add(i, value) }

                            // Add value processor to cache starting after list item
                            addToCache(offset) { q ->
                                this.readQualifier(q, offset, select, listValueAdder, readValueFromStorage, addToCache)
                            }

                            addValueToOutput(index, list)
                        } else {
                            // Ensure that next list values are not read
                            addToCache(offset) {
                                // Ignore reading and return
                            }
                        }
                    } else {
                        val itemIndex = initUInt(reader = {
                            qualifier[qIndex++]
                        }).toInt()

                        if (qualifier.size > qIndex) {
                            throw ParseException("Lists cannot contain complex data")
                        }

                        // Read list item
                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, definition)?.let {
                            // Only add to output if value read from storage is not null
                            addValueToOutput(itemIndex, it)
                        }
                    }
                }
                SET -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a set size
                        @Suppress("UNCHECKED_CAST")
                        val setSize = readValueFromStorage(
                            SetSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                            definition
                        ) as Int?

                        if (setSize != null) {
                            // If not null we can create a set of setSize
                            val set = LinkedHashSet<Any>(setSize)
                            val setValueAdder: AddToValues = { _, value -> set += value }

                            addToCache(offset) { q ->
                                this.readQualifier(q, offset, select, setValueAdder, readValueFromStorage, addToCache)
                            }

                            addValueToOutput(index, set)
                        } else {
                            // Ensure that next set values are not read
                            addToCache(offset) {
                                // Ignore reading and return
                            }
                        }
                    } else {
                        // Read set contents. Always a simple value for set since it is in qualifier
                        val valueDefinition =
                            ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                        val key = valueDefinition.readStorageBytes(qualifier.size - qIndex) { qualifier[qIndex++] }

                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, definition)?.let {
                            // Only add to output if value read from storage is not null
                            addValueToOutput(index, key)
                        }
                    }
                }
                MAP -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a map count
                        @Suppress("UNCHECKED_CAST")
                        val mapSize = readValueFromStorage(
                            MapSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                            definition
                        ) as Int?

                        if (mapSize != null) {
                            // If not null we can create a map of mapSize
                            val map = LinkedHashMap<Any, Any>(mapSize)

                            @Suppress("UNCHECKED_CAST")
                            val mapValueAdder: AddToValues = { _, value ->
                                val (k, v) = value as Pair<Any, Any>
                                map[k] = v
                            }

                            // For later map items the above map value adder is used
                            addToCache(offset) { q ->
                                this.readQualifier(q, offset, select, mapValueAdder, readValueFromStorage, addToCache)
                            }

                            addValueToOutput(index, map)
                        } else {
                            // Ensure that next map values are not read
                            addToCache(offset) {
                                // Ignore reading and return
                            }
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val mapDefinition = definition.definition as? IsMapDefinition<Any, Any, *>
                            ?: throw TypeException("Definition ${definition.definition} should be a MapDefinition")
                        val keyDefinition = mapDefinition.keyDefinition
                        val qualifierReader = { qualifier[qIndex++] }

                        val keySize = initIntByVar(qualifierReader)
                        val key = keyDefinition.readStorageBytes(keySize, qualifierReader)

                        // Create map Item adder
                        val mapItemAdder: AddValue = { value ->
                            addValueToOutput(index, Pair(key, value))
                        }

                        // Begin to read map value
                        if (qualifier.size <= qIndex) {
                            @Suppress("UNCHECKED_CAST")
                            val value = readValueFromStorage(
                                Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                                mapDefinition.valueDefinition
                            )

                            when {
                                value == null ->
                                    // Ensure that next map values are not read because they are deleted
                                    addToCache(qIndex - 1) {
                                        // Ignore reading and return
                                    }
                                value != Unit -> mapItemAdder(value)
                                else -> null
                            }
                        } else {
                            when (val valueDefinition = mapDefinition.valueDefinition) {
                                is IsMultiTypeDefinition<*, *> -> {
                                    readTypedValue(
                                        qualifier,
                                        qIndex,
                                        readValueFromStorage,
                                        valueDefinition,
                                        select,
                                        addToCache,
                                        mapItemAdder
                                    )
                                }
                                is IsEmbeddedDefinition<*, *> -> {
                                    readEmbeddedValues(
                                        valueDefinition as IsEmbeddedDefinition<*, *>,
                                        select,
                                        readValueFromStorage,
                                        addToCache,
                                        qualifier,
                                        qIndex,
                                        mapItemAdder
                                    )
                                }
                                else -> throw StorageException("Can only use Embedded/MultiType as complex value type in Map $mapDefinition")
                            }
                        }
                    }
                }
                ReferenceType.TYPE -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")
                    @Suppress("UNCHECKED_CAST")
                    val typedDefinition = definition.definition as? IsMultiTypeDefinition<AnyIndexedEnum, *>
                        ?: throw TypeException("Definition($index) ${definition.definition} should be a TypedDefinition")

                    typedDefinition.readComplexTypedValue(
                        index.toUInt(),
                        qualifier,
                        qIndex,
                        readValueFromStorage,
                        select,
                        addToCache,
                        { addValueToOutput(index, it) })
                }
            }
        }
    }
}

private fun readComplexValueFromStorage(
    definition: AnyPropertyDefinitionWrapper,
    qualifier: ByteArray,
    qIndex: Int,
    readValueFromStorage: ValueReader,
    valueAdder: AddValue,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addValueToOutput: AddToValues,
    index: Int
) {
    when (definition) {
        is IsMultiTypeDefinition<*, *> -> {
            readTypedValue(
                qualifier,
                qIndex,
                readValueFromStorage,
                definition,
                select,
                addToCache,
                valueAdder
            )
        }
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition,
                select,
                readValueFromStorage,
                addToCache,
                qualifier,
                qIndex,
                { addValueToOutput(index, it) }
            )
        }
        else -> throw StorageException("Can only use Embedded/Multi as values with deeper values, not $definition")
    }
}

/** Read a typed value */
private fun readTypedValue(
    qualifier: ByteArray,
    offset: Int,
    readValueFromStorage: ValueReader,
    valueDefinition: IsMultiTypeDefinition<*, *>,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addValueToOutput: AddValue,
    typeToCheck: IndexedEnum<*>? = null
) {
    var qIndex1 = offset
    if (qualifier.size <= qIndex1) {
        @Suppress("UNCHECKED_CAST")
        readValueFromStorage(
            Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
            valueDefinition as IsPropertyDefinition<Any>
        )?.let {
            // Pass type to check
            addToCache(qIndex1 - 1) { q ->
                readTypedValue(
                    qualifier = q,
                    offset = qIndex1,
                    readValueFromStorage = readValueFromStorage,
                    valueDefinition = valueDefinition,
                    select = select,
                    addToCache = addToCache,
                    addValueToOutput = addValueToOutput,
                    typeToCheck = (it as TypedValue<*, *>).type
                )
            }

            addValueToOutput(it)
        }
    } else {
        initUIntByVarWithExtraInfo({ qualifier[qIndex1++] }) { typeIndex, _ ->
            typeToCheck?.let {
                // Skip values if type does not check out
                if (typeToCheck.index != typeIndex) {
                    return@initUIntByVarWithExtraInfo
                }
            }

            valueDefinition.readComplexTypedValue(
                typeIndex,
                qualifier,
                qIndex1,
                readValueFromStorage,
                select,
                addToCache,
                addValueToOutput
            )
        }
    }
}

/** Read a complex Typed value from qualifier */
private fun IsMultiTypeDefinition<*, *>.readComplexTypedValue(
    index: UInt,
    qualifier: ByteArray,
    qIndex: Int,
    readValueFromStorage: ValueReader,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor,
    addValueToOutput: AddValue
) {
    val definition = this.definition(index)
        ?: throw DefNotFoundException("No definition for $index in $this")
    @Suppress("UNCHECKED_CAST")
    val type = this.typeEnum.valueByIndex[index] as AnyIndexedEnum?
        ?: throw DefNotFoundException("Unknown type $index for $this")

    val addMultiTypeToOutput: AddValue = { addValueToOutput(TypedValue(type, it)) }

    if (qualifier.size <= qIndex) {
        @Suppress("UNCHECKED_CAST")
        val value = readValueFromStorage(
            Embed as StorageTypeEnum<IsPropertyDefinition<Any>>,
            definition as IsPropertyDefinition<Any>
        )

        if (value == null) {
            // Ensure that next values are not read because Values is deleted
            addToCache(qIndex - 1) {}
        }
        // Dont process further
        return
    }

    when (definition) {
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition as IsEmbeddedDefinition<*, *>,
                select,
                readValueFromStorage,
                addToCache,
                qualifier,
                qIndex,
                addMultiTypeToOutput
            )
        }
        else -> throw InvalidDefinitionException("Can only use Embedded/MultiType as complex value type in Multi Type $definition")
    }
}

/** Read embedded values into Values object */
private fun <P : PropertyDefinitions> readEmbeddedValues(
    definition: IsEmbeddedDefinition<*, *>,
    select: IsPropRefGraph<P>?,
    readValueFromStorage: ValueReader,
    addToCache: CacheProcessor,
    qualifier: ByteArray,
    offset: Int,
    addValueToOutput: AddValue
) {
    @Suppress("UNCHECKED_CAST")
    val dataModel = definition.dataModel as IsDataModelWithValues<*, PropertyDefinitions, *>
    val values = dataModel.values { MutableValueItems() }

    addValueToOutput(values)

    val valuesItemAdder: AddToValues = { i, value ->
        values.add(i, value)
    }

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
            valuesItemAdder,
            readValueFromStorage,
            addToCache
        )
    }

    dataModel.readQualifier(
        qualifier,
        offset,
        specificSelect,
        valuesItemAdder,
        readValueFromStorage,
        addToCache
    )
}
