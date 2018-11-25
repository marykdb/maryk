@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.extensions.bytes.initUInt
import maryk.core.models.IsDataModel
import maryk.core.models.IsDataModelWithValues
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.ListCount
import maryk.core.processors.datastore.StorageTypeEnum.MapCount
import maryk.core.processors.datastore.StorageTypeEnum.SetCount
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsAnyEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.references.ReferenceSpecialType.DELETE
import maryk.core.properties.references.ReferenceSpecialType.MAP_KEY
import maryk.core.properties.references.ReferenceSpecialType.TYPE
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.SPECIAL
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.referenceStorageSpecialTypeOf
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

private typealias QualifierProcessor = (ByteArray) -> Unit
private typealias CacheProcessor = (Int, QualifierProcessor) -> Unit
typealias ValueReader = (StorageTypeEnum<IsPropertyDefinition<Any>>, IsPropertyDefinition<Any>?) -> Any
typealias ValueAdder = (Int, Any) -> Unit

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.convertStorageToValues(
    getQualifier: () -> ByteArray?,
    processValue: ValueReader
): Values<DM, P> {
    val mutableValuesItems = MutableValueItems()

    val valueAdder: ValueAdder = { index: Int, value: Any ->
        mutableValuesItems += ValueItem(index, value)
    }

    var lastQualifier: ByteArray? = null
    val processorList = mutableListOf<Pair<Int, QualifierProcessor>>()
    var qualifier = getQualifier()

    while (qualifier != null) {
        lastQualifier?.let { last ->
            val nonMatchIndex = last.firstNonMatchIndex(qualifier!!)
            for (i in (processorList.size - 1 downTo 0)) {
                if (processorList[i].first >= nonMatchIndex) {
                    processorList.removeAt(i)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        processorList.lastOrNull()?.second?.invoke(qualifier) ?:
            (this as IsDataModel<AbstractPropertyDefinitions<*>>).readQualifier(qualifier, 0, valueAdder, processValue) { index, processor ->
                processorList += Pair(index, processor)
            }

        lastQualifier = qualifier

        qualifier = getQualifier()
    }

    return this.values(null) {
        mutableValuesItems
    }
}

/**
 * Find the first non match index against [comparedTo]
 */
private fun ByteArray.firstNonMatchIndex(comparedTo: ByteArray): Int {
    var index = -1
    while (++index < this.size) {
        if (comparedTo[index] != this[index]) break
    }
    return index
}

/**
 * Read specific [qualifier] from [offset].
 * [addValueToOutput] is used to add values to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache so it does not need to reprocess qualifier from start
 */
private fun <P: AbstractPropertyDefinitions<*>> IsDataModel<P>.readQualifier(
    qualifier: ByteArray,
    offset: Int,
    addValueToOutput: ValueAdder,
    readValueFromStorage: ValueReader,
    addToCache: CacheProcessor
) {
    addToCache(offset) { q ->
        this.readQualifier(q, offset, addValueToOutput, readValueFromStorage, addToCache)
    }

    var qIndex = offset

    initIntByVarWithExtraInfo({ qualifier[qIndex++] }) { index, type ->
        val isAtEnd = qualifier.size <= qIndex
        when (referenceStorageTypeOf(type)) {
            SPECIAL -> when (val specialType = referenceStorageSpecialTypeOf(qualifier[offset])) {
                DELETE -> {} // ignore
                TYPE, MAP_KEY -> throw Exception("Cannot handle Special type $specialType in qualifier")
            }
            VALUE -> readValue(isAtEnd, index, qualifier, qIndex, addValueToOutput, readValueFromStorage, addToCache)
            LIST -> if (isAtEnd) {
                @Suppress("UNCHECKED_CAST")
                val listCount = readValueFromStorage(
                    ListCount as StorageTypeEnum<IsPropertyDefinition<Any>>,
                    this.properties[index]!!
                ) as Int

                val list = ArrayList<Any>(listCount)
                val listValueAdder: ValueAdder = { i, value -> list.add(i, value) }

                addToCache(offset) { q ->
                    this.readQualifier(q, offset, listValueAdder, readValueFromStorage, addToCache)
                }

                addValueToOutput(index, list)
            } else {
                var listItemIndex = qIndex
                val itemIndex = initUInt(reader = {
                    qualifier[listItemIndex++]
                }).toInt()

                if (qualifier.size > listItemIndex) {
                    throw ParseException("Lists cannot contain complex data")
                }

                @Suppress("UNCHECKED_CAST")
                addValueToOutput(
                    itemIndex,
                    readValueFromStorage(
                        Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    )
                )
            }
            SET -> if (isAtEnd) {
                @Suppress("UNCHECKED_CAST")
                val set = LinkedHashSet<Any>(
                    readValueFromStorage(
                        SetCount as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    ) as Int
                )

                val setValueAdder: ValueAdder = { _, value -> set += value }

                addToCache(offset) { q ->
                    this.readQualifier(q, offset, setValueAdder, readValueFromStorage, addToCache)
                }

                addValueToOutput(index, set)
            } else {
                val valueDefinition = ((this.properties[index]!! as IsSetDefinition<*, *>).valueDefinition as IsFixedBytesEncodable<*>)
                var setItemIndex = qIndex
                val key = valueDefinition.readStorageBytes(valueDefinition.byteSize) { qualifier[setItemIndex++] }

                if (qualifier.size > setItemIndex) {
                    throw ParseException("Sets cannot contain complex data")
                }

                addValueToOutput(index, key)
            }
            MAP -> if (isAtEnd) {
                @Suppress("UNCHECKED_CAST")
                val map = LinkedHashMap<Any, Any>(
                    readValueFromStorage(
                        MapCount as StorageTypeEnum<IsPropertyDefinition<Any>>,
                        this.properties[index]!!
                    ) as Int
                )

                @Suppress("UNCHECKED_CAST")
                val mapValueAdder: ValueAdder = { _, value ->
                    val (k, v) = value as Pair<Any, Any>
                    map[k] = v
                }

                // For later map items the above map value adder is used
                addToCache(offset) { q ->
                    this.readQualifier(q, offset, mapValueAdder, readValueFromStorage, addToCache)
                }

                addValueToOutput(index, map)
            } else {
                val keyDefinition = ((this.properties[index]!! as IsMapDefinition<*, *, *>).keyDefinition as IsFixedBytesEncodable<*>)
                var mapItemIndex = qIndex
                val key = keyDefinition.readStorageBytes(keyDefinition.byteSize) { qualifier[mapItemIndex++] }
                val mapItemAdder: ValueAdder = { i, value ->
                    addValueToOutput(i, Pair(key, value))
                }

                this.readValue(
                    qualifier.size <= mapItemIndex,
                    index,
                    qualifier,
                    mapItemIndex,
                    mapItemAdder,
                    readValueFromStorage,
                    addToCache
                )
            }
        }
    }
}

/**
 * Reads a specific value type
 * [isAtEnd] if qualifier is at the end
 * [index] of the item to be injected in output
 * [qualifier] in storage of current value
 * [offset] in bytes from where qualifier
 * [addValueToOutput] / [readValueFromStorage]
 * [addToCache] so next qualifiers do not need to reprocess qualifier
 */
private fun <P : AbstractPropertyDefinitions<*>> IsDataModel<P>.readValue(
    isAtEnd: Boolean,
    index: Int,
    qualifier: ByteArray,
    offset: Int,
    addValueToOutput: ValueAdder,
    readValueFromStorage: ValueReader,
    addToCache: CacheProcessor
) {
    if (isAtEnd) {
        @Suppress("UNCHECKED_CAST")
        addValueToOutput(
            index,
            readValueFromStorage(
                Value as StorageTypeEnum<IsPropertyDefinition<Any>>,
                this.properties[index]!!
            )
        )
    } else {
        when (val definition = this.properties[index]) {
            is IsEmbeddedDefinition<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val dataModel = (definition as IsAnyEmbeddedDefinition).dataModel as IsDataModelWithValues<*, *, *>
                val values = dataModel.values { MutableValueItems() }

                addValueToOutput(index, values)

                val valuesItemAdder: ValueAdder = { i, value ->
                    values.add(i, value)
                }

                addToCache(offset - 1) { q ->
                    dataModel.readQualifier(q, offset, valuesItemAdder, readValueFromStorage, addToCache)
                }

                dataModel.readQualifier(
                    qualifier,
                    offset,
                    valuesItemAdder,
                    readValueFromStorage,
                    addToCache
                )
            }
            else -> throw Exception("Can only use Embedded as values with deeper values $definition")
        }
    }
}
