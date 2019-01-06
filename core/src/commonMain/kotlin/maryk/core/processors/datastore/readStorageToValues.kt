@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.extensions.bytes.initUInt
import maryk.core.models.IsDataModel
import maryk.core.models.IsDataModelWithValues
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.values
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
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.ReferenceType
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
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.convertStorageToValues(
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
private fun <P: PropertyDefinitions> IsDataModel<P>.readQualifier(
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
                    DELETE -> {} // Ignore since it should be handled on higher level
                    MAP_KEY -> throw Exception("Cannot handle Special type $specialType in qualifier")
                    else -> throw Exception("Not recognized special type $specialType")
                }
                VALUE -> {
                    val definition = this.properties[index]
                        ?: throw Exception("No definition for $index in $this at $index")
                    val valueAdder: AddValue = { addValueToOutput(index, it) }

                    if (isAtEnd) {
                        @Suppress("UNCHECKED_CAST")
                        readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, definition)?.let(valueAdder)
                    } else {
                        when (definition) {
                            is IsMultiTypeDefinition<*, *> -> {
                                readTypedValue(
                                    qualifier,
                                    qIndex,
                                    readValueFromStorage,
                                    definition,
                                    valueAdder,
                                    select,
                                    addToCache
                                )
                            }
                            is IsEmbeddedDefinition<*, *> -> {
                                readEmbeddedValues(definition, { addValueToOutput(index, it) }, select, addToCache, qIndex, readValueFromStorage, qualifier)
                            }
                            else -> throw Exception("Can only use Embedded as values with deeper values, not $definition")
                        }
                    }
                }
                LIST -> {
                    val definition = this.properties[index]
                        ?: throw Exception("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a list size
                        @Suppress("UNCHECKED_CAST")
                        val listSize = readValueFromStorage(ListSize as StorageTypeEnum<IsPropertyDefinition<Any>>, definition) as Int?

                        if (listSize != null) {
                            // If not null we can create an empty list of listSize
                            val list = ArrayList<Any>(listSize)
                            val listValueAdder: AddToValues = { i, value -> list.add(i, value) }

                            // Add value processor to cache starting after list item
                            addToCache(offset) { q ->
                                this.readQualifier(q, offset, select, listValueAdder, readValueFromStorage, addToCache)
                            }

                            addValueToOutput(index, list)
                        } else null
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
                        ?: throw Exception("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a set size
                        @Suppress("UNCHECKED_CAST")
                        val setSize = readValueFromStorage(SetSize as StorageTypeEnum<IsPropertyDefinition<Any>>, definition) as Int?

                        if (setSize != null) {
                            // If not null we can create a set of setSize
                            val set = LinkedHashSet<Any>(setSize)
                            val setValueAdder: AddToValues = { _, value -> set += value }

                            addToCache(offset) { q ->
                                this.readQualifier(q, offset, select, setValueAdder, readValueFromStorage, addToCache)
                            }

                            addValueToOutput(index, set)
                        } else null
                    } else {
                        // Read set contents. Always a simple value for set since it is in qualifier
                        val valueDefinition = ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
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
                        ?: throw Exception("No definition for $index in $this at $index")

                    if (isAtEnd) {
                        // If at end it means that this is a map count
                        @Suppress("UNCHECKED_CAST")
                        val mapSize = readValueFromStorage(MapSize as StorageTypeEnum<IsPropertyDefinition<Any>>, definition) as Int?

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
                        } else null
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val mapDefinition = definition.definition as? IsMapDefinition<Any, Any, *> ?: throw Exception("Definition ${definition.definition} should be a MapDefinition")
                        val keyDefinition = mapDefinition.keyDefinition
                        val qualifierReader = {qualifier[qIndex++]}

                        val keySize = initIntByVar(qualifierReader)
                        val key = keyDefinition.readStorageBytes(keySize, qualifierReader)

                        // Create map Item adder
                        val mapItemAdder: AddValue = { value ->
                            addValueToOutput(index, Pair(key, value))
                        }

                        // Begin to read map value
                        if (qualifier.size <= qIndex) {
                            @Suppress("UNCHECKED_CAST")
                            readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, mapDefinition.valueDefinition)?.let {
                                mapItemAdder(it)
                            }
                        } else {
                            when (val valueDefinition = mapDefinition.valueDefinition) {
                                is IsMultiTypeDefinition<*, *> -> {
                                    readTypedValue(
                                        qualifier,
                                        qIndex,
                                        readValueFromStorage,
                                        valueDefinition,
                                        mapItemAdder,
                                        select,
                                        addToCache
                                    )
                                }
                                is IsEmbeddedDefinition<*, *> -> {
                                    readEmbeddedValues(
                                        valueDefinition as IsEmbeddedDefinition<*, *>,
                                        mapItemAdder,
                                        select,
                                        addToCache,
                                        qIndex,
                                        readValueFromStorage,
                                        qualifier
                                    )
                                }
                                else -> throw Exception("Can only use Embedded/MultiType as complex value type in Map $mapDefinition")
                            }
                        }
                    }
                }
                ReferenceType.TYPE -> {
                    val definition = this.properties[index]
                        ?: throw Exception("No definition for $index in $this at $index")
                    @Suppress("UNCHECKED_CAST")
                    val typedDefinition = definition.definition as? IsMultiTypeDefinition<*, *>
                        ?: throw Exception("Definition($index) ${definition.definition} should be a TypedDefinition")

                    typedDefinition.readComplexTypedValue(index, { addValueToOutput(index, it) }, qualifier, qIndex, readValueFromStorage, select, addToCache)
                }
            }
        }
    }
}

/** Read a typed value */
private fun readTypedValue(
    qualifier: ByteArray,
    offset: Int,
    readValueFromStorage: ValueReader,
    valueDefinition: IsMultiTypeDefinition<*, *>,
    mapItemAdder: AddValue,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor
) {
    var qIndex1 = offset
    if (qualifier.size <= qIndex1) {
        @Suppress("UNCHECKED_CAST")
        readValueFromStorage(Value as StorageTypeEnum<IsPropertyDefinition<Any>>, valueDefinition as IsPropertyDefinition<Any>)?.let {
            mapItemAdder(it)
        }
    } else {
        initIntByVarWithExtraInfo({ qualifier[qIndex1++] }) { typeIndex, _ ->
            valueDefinition.readComplexTypedValue(
                typeIndex,
                mapItemAdder,
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
private fun IsMultiTypeDefinition<*, *>.readComplexTypedValue(
    index: Int,
    addValueToOutput: AddValue,
    qualifier: ByteArray,
    qIndex: Int,
    readValueFromStorage: ValueReader,
    select: IsPropRefGraph<*>?,
    addToCache: CacheProcessor
) {
    val definition = this.definition(index)
    @Suppress("UNCHECKED_CAST")
    val type: IndexedEnum<IndexedEnum<*>> =
        this.type(index) as IndexedEnum<IndexedEnum<*>>? ?: throw Exception("Unknown type $index for $this")

    val addMultiTypeToOutput: AddValue = { addValueToOutput(TypedValue(type, it)) }

    if (qualifier.size <= qIndex) {
        throw Exception("Type in qualifier should only be used for complex types")
    }

    when (definition) {
        is IsEmbeddedDefinition<*, *> -> {
            readEmbeddedValues(
                definition as IsEmbeddedDefinition<*, *>,
                addMultiTypeToOutput,
                select,
                addToCache,
                qIndex,
                readValueFromStorage,
                qualifier
            )
        }
        else -> throw Exception("Can only use Embedded/MultiType as complex value type in Multi Type $definition")
    }
}

/** Read embedded values into Values object */
private fun <P : PropertyDefinitions> readEmbeddedValues(
    definition: IsEmbeddedDefinition<*, *>,
    addValueToOutput: AddValue,
    select: IsPropRefGraph<P>?,
    addToCache: CacheProcessor,
    offset: Int,
    readValueFromStorage: ValueReader,
    qualifier: ByteArray
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
        dataModel.readQualifier(q, offset, specificSelect, valuesItemAdder, readValueFromStorage, addToCache)
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
