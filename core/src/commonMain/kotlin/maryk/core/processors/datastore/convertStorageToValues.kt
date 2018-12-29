@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

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
import maryk.core.properties.definitions.IsAnyEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.MAP_KEY
import maryk.core.properties.references.CompleteReferenceType.TYPE
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.completeReferenceTypeOf
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

typealias ValueReader = (StorageTypeEnum<IsPropertyDefinition<Any>>, IsPropertyDefinition<Any>?) -> Any?
private typealias ValueAdder = (Int, Any) -> Unit

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
    val valueAdder: ValueAdder = { index: Int, value: Any ->
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
    select: IsPropRefGraph<P>?,
    addValueToOutput: ValueAdder,
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
                    DELETE -> {} // ignore
                    TYPE, MAP_KEY -> throw Exception("Cannot handle Special type $specialType in qualifier")
                    else -> throw Exception("Not recognized special type $specialType")
                }
                VALUE -> readValue(isAtEnd, index, qualifier, select, qIndex, addValueToOutput, readValueFromStorage, addToCache)
                LIST -> if (isAtEnd) {
                    // If at end it means that this is a list size
                    @Suppress("UNCHECKED_CAST")
                    val listSize = readValueFromStorage(
                        ListSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    ) as Int?

                    if (listSize != null) {
                        // If not null we can create an empty list of listSize
                        val list = ArrayList<Any>(listSize)
                        val listValueAdder: ValueAdder = { i, value -> list.add(i, value) }

                        // Add value processor to cache starting after list item
                        addToCache(offset) { q ->
                            this.readQualifier(q, offset, select, listValueAdder, readValueFromStorage, addToCache)
                        }

                        addValueToOutput(index, list)
                    } else null
                } else {
                    // First read list item index
                    var listItemIndex = qIndex
                    val itemIndex = initUInt(reader = {
                        qualifier[listItemIndex++]
                    }).toInt()

                    if (qualifier.size > listItemIndex) {
                        throw ParseException("Lists cannot contain complex data")
                    }

                    // Read list item
                    @Suppress("UNCHECKED_CAST")
                    readValueFromStorage(
                        Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    )?.let {
                        // Only add to output if value read from storage is not null
                        addValueToOutput(itemIndex, it)
                    }
                }
                SET -> if (isAtEnd) {
                    // If at end it means that this is a set size
                    @Suppress("UNCHECKED_CAST")
                    val setSize = readValueFromStorage(
                        SetSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    ) as Int?

                    if (setSize != null) {
                        // If not null we can create a set of setSize
                        val set = LinkedHashSet<Any>(setSize)

                        val setValueAdder: ValueAdder = { _, value -> set += value }

                        addToCache(offset) { q ->
                            this.readQualifier(q, offset, select, setValueAdder, readValueFromStorage, addToCache)
                        }

                        addValueToOutput(index, set)
                    } else null
                } else {
                    // Read set contents. Always a simple value for set since it is in qualifier
                    val valueDefinition = ((this.properties[index]!! as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                    var setItemIndex = qIndex

                    val key = valueDefinition.readStorageBytes(qualifier.size - qIndex) { qualifier[setItemIndex++] }

                    addValueToOutput(index, key)
                }
                MAP -> if (isAtEnd) {
                    // If at end it means that this is a map count
                    @Suppress("UNCHECKED_CAST")
                    val mapSize = readValueFromStorage(
                        MapSize as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    ) as Int?

                    if (mapSize != null) {
                        // If not null we can create a map of mapSize
                        val map = LinkedHashMap<Any, Any>(mapSize)

                        @Suppress("UNCHECKED_CAST")
                        val mapValueAdder: ValueAdder = { _, value ->
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
                    // Read key first
                    val keyDefinition =
                        ((this.properties[index]!! as IsMapDefinition<*, *, *>).keyDefinition as IsFixedBytesEncodable<*>)
                    var mapItemIndex = qIndex
                    val key = keyDefinition.readStorageBytes(keyDefinition.byteSize) { qualifier[mapItemIndex++] }

                    // Create map Item adder
                    val mapItemAdder: ValueAdder = { i, value ->
                        addValueToOutput(i, Pair(key, value))
                    }

                    // Begin to read map value
                    this.readValue(
                        qualifier.size <= mapItemIndex,
                        index,
                        qualifier,
                        select,
                        mapItemIndex,
                        mapItemAdder,
                        readValueFromStorage,
                        addToCache
                    )
                }
            }
        }
    }
}

/**
 * Reads a specific value type
 * [isAtEnd] if qualifier is at the end
 * [index] of the item to be injected in output
 * [qualifier] in storage of current value
 * [select] to only process selected values
 * [offset] in bytes from where qualifier
 * [addValueToOutput] / [readValueFromStorage]
 * [addToCache] so next qualifiers do not need to reprocess qualifier
 */
private fun <P : PropertyDefinitions> IsDataModel<P>.readValue(
    isAtEnd: Boolean,
    index: Int,
    qualifier: ByteArray,
    select: IsPropRefGraph<P>?,
    offset: Int,
    addValueToOutput: ValueAdder,
    readValueFromStorage: ValueReader,
    addToCache: CacheProcessor
) {
    if (isAtEnd) {
        @Suppress("UNCHECKED_CAST")
        readValueFromStorage(
            Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
            this.properties[index]!!
        )?.let {
            addValueToOutput(index, it)
        }
    } else {
        when (val definition = this.properties[index]) {
            is IsEmbeddedDefinition<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val dataModel = (definition as IsAnyEmbeddedDefinition).dataModel as IsDataModelWithValues<*, PropertyDefinitions, *>
                val values = dataModel.values { MutableValueItems() }

                addValueToOutput(index, values)

                val valuesItemAdder: ValueAdder = { i, value ->
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
            else -> throw Exception("Can only use Embedded as values with deeper values $definition")
        }
    }
}
