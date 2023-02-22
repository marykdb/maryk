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
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.references.ObjectDeleteReference
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.DELETE
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.referenceStorageTypeOf
import maryk.core.properties.types.TypedValue
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException

typealias ValueReader = (StorageTypeEnum<IsPropertyDefinition<out Any>>, IsPropertyReferenceForCache<*, *>) -> Any?
private typealias AddToValues = (UInt, Any) -> Unit
private typealias AddValue = (Any) -> Unit

/**
 * Convert storage bytes to values.
 * [getQualifier] gets a qualifier until none is available and returns null
 * [processValue] processes the storage value with given type and definition
 */
fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> DM.readStorageToValues(
    getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean,
    select: RootPropRefGraph<P>?,
    processValue: ValueReader
): Values<DM, P> {
    // Used to collect all found ValueItems
    val mutableValuesItems = MutableValueItems()

    // Adds valueItems to collection
    val valueAdder: AddToValues = { index, value ->
        mutableValuesItems += ValueItem(index, value)
    }

    processQualifiers(getQualifier) { qualifierReader, qualifierLength, addToCache ->
        // Otherwise, try to get a new qualifier processor from DataModel
        (this as IsDataModel<P>).readQualifier(qualifierReader, qualifierLength, 0, select, null, valueAdder, processValue, addToCache)
    }

    // Create Values
    return this.values(null) {
        mutableValuesItems
    }
}

/**
 * Read specific [qualifierReader] from [offset].
 * [addValueToOutput] is used to add values to output
 * [readValueFromStorage] is used to fetch actual value from storage layer
 * [addToCache] is used to add a sub reader to cache, so it does not need to reprocess the qualifier from start
 */
private fun <P : IsValuesPropertyDefinitions> IsDataModel<P>.readQualifier(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    select: IsPropRefGraph<*>?,
    parentReference: IsPropertyReference<*, *, *>?,
    addValueToOutput: AddToValues,
    readValueFromStorage: ValueReader,
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
                    readValueFromStorage(ObjectDelete, ObjectDeleteReference)
                }
                else -> {
                    val definition = this.properties[index]
                        ?: throw DefNotFoundException("No definition for $index in $this at $index")

                    readQualifierOfType(
                        qualifierReader,
                        qualifierLength,
                        currentOffset,
                        offset,
                        definition.definition,
                        index,
                        refStoreType,
                        select,
                        definition.ref(parentReference),
                        addValueToOutput,
                        readValueFromStorage,
                        addToCache
                    )
                }
            }
        }
    }
}

/** Read qualifier from [qualifierReader] at [currentOffset] with [definition] into a value */
private fun readQualifierOfType(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    currentOffset: Int,
    partOffset: Int,
    definition: IsPropertyDefinition<out Any>,
    index: UInt,
    refStoreType: ReferenceType,
    select: IsPropRefGraph<*>?,
    reference: IsPropertyReference<*, *, *>,
    addValueToOutput: AddToValues,
    readValueFromStorage: ValueReader,
    addToCache: CacheProcessor
): Unit? {
    var offset = currentOffset
    val isAtEnd = qualifierLength <= offset

    return when (refStoreType) {
        DELETE -> {
            // skip
        }
        VALUE -> {
            val valueAdder: AddValue = { addValueToOutput(index, it) }

            if (isAtEnd) {
                @Suppress("UNCHECKED_CAST")
                when (val value = readValueFromStorage(Value, reference)) {
                    null -> // Ensure that next potential embedded values will not be read because is deleted
                        addToCache(partOffset) { _, _ ->
                            // Ignore reading and return
                        }
                    is TypedValue<TypeEnum<Any>, Any> -> readTypedValue(
                        qualifierReader = qualifierReader,
                        qualifierLength = qualifierLength,
                        offset = offset,
                        readValueFromStorage = readValueFromStorage,
                        valueDefinition = definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                        select = select,
                        parentReference = reference,
                        addToCache = addToCache,
                        addValueToOutput = valueAdder
                    )
                    else -> valueAdder(value)
                }
            } else {
                throw ParseException("Only allowed complex value qualifier is for multi type and that is handled through a cached reader")
            }
        }
        EMBED -> {
            val valueAdder: AddValue = { addValueToOutput(index, it) }

            if (isAtEnd) {
                val embedValue = readValueFromStorage(Embed, reference)
                if (embedValue == null) {
                    // Ensure that next embedded values will not be read
                    addToCache(partOffset) { _, _ ->
                        // Ignore reading and return
                    }
                } else null // unknown value so ignore
            } else {
                // Only embedded types can be encoded as complex EMBED qualifier
                if (definition !is IsEmbeddedValuesDefinition<*, *, *>) {
                    throw TypeException("Only Embeds types are allowed to be encoded as EMBED type with complex qualifiers. Not $definition")
                }
                readEmbeddedValues(
                    qualifierReader,
                    qualifierLength,
                    offset,
                    readValueFromStorage,
                    definition,
                    reference,
                    select,
                    addToCache,
                    valueAdder
                )
            }
        }
        LIST -> {
            if (isAtEnd) {
                // If at the end it means that this is a list size
                val listSize = readValueFromStorage(ListSize, reference) as Int?

                if (listSize != null) {
                    // If not null we can create an empty list of listSize
                    val list = ArrayList<Any>(listSize)
                    val listValueAdder: AddToValues = { i, value -> list.add(i.toInt(), value) }

                    // Add value processor to cache starting after list item
                    addToCache(partOffset) { qr, l ->
                        readQualifierOfType(qr, l, currentOffset, partOffset, definition, index, refStoreType, select, reference, listValueAdder, readValueFromStorage, addToCache)
                    }

                    addValueToOutput(index, list)
                } else {
                    // Ensure that next list values will not be read
                    addToCache(partOffset) { _, _ ->
                        // Ignore reading and return
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val listDefinition = definition as IsListDefinition<Any, IsPropertyContext>
                @Suppress("UNCHECKED_CAST")
                val listReference = reference as CanContainListItemReference<*, *, *>

                val listIndex = initUInt({ qualifierReader(offset++) })
                val itemReference = listDefinition.itemRef(listIndex, listReference)

                if (qualifierLength > offset) {
                    throw ParseException("Lists cannot contain complex data")
                }

                // Read list item
                readValueFromStorage(Value, itemReference)?.let {
                    // Only add to output if value read from storage is not null
                    addValueToOutput(listIndex, it)
                }
            }
        }
        SET -> {
            if (isAtEnd) {
                // If at the end it means that this is a set size
                val setSize = readValueFromStorage(SetSize, reference) as Int?

                if (setSize != null) {
                    // If not null we can create a set of setSize
                    val set = LinkedHashSet<Any>(setSize)
                    val setValueAdder: AddToValues = { _, value -> set += value }

                    addToCache(partOffset) { qr, l ->
                        readQualifierOfType(qr, l, currentOffset, partOffset, definition, index, refStoreType, select, reference, setValueAdder, readValueFromStorage, addToCache)
                    }

                    addValueToOutput(index, set)
                } else {
                    // Ensure that next set values will not be read
                    addToCache(partOffset) { _, _ ->
                        // Ignore reading and return
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val setDefinition = definition as IsSetDefinition<Any, IsPropertyContext>
                @Suppress("UNCHECKED_CAST")
                val setReference = reference as CanContainSetItemReference<*, *, *>

                // Read set contents. It is always a simple value for set since it is in the qualifier.
                val valueDefinition =
                    ((definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<*, *>)
                val setItemLength = initIntByVar { qualifierReader(offset++) }
                val key = valueDefinition.readStorageBytes(setItemLength) { qualifierReader(offset++) }

                val setItemReference = setDefinition.itemRef(key, setReference)

                readValueFromStorage(Value, setItemReference)?.let {
                    // Only add to output if value read from storage is not null
                    addValueToOutput(index, key)
                }
            }
        }
        MAP -> {
            if (isAtEnd) {
                // If at the end it means that this is a map count
                val mapSize = readValueFromStorage(MapSize, reference) as Int?

                if (mapSize != null) {
                    // If not null we can create a map of mapSize
                    val map = LinkedHashMap<Any, Any>(mapSize)

                    @Suppress("UNCHECKED_CAST")
                    val mapValueAdder: AddToValues = { _, value ->
                        val (k, v) = value as Pair<Any, Any>
                        map[k] = v
                    }

                    // For later map items the above map value adder will be used
                    addToCache(partOffset) { qr, l ->
                        readQualifierOfType(qr, l, currentOffset, partOffset, definition, index, refStoreType, select, reference, mapValueAdder, readValueFromStorage, addToCache)
                    }

                    addValueToOutput(index, map)
                } else {
                    // Ensure that next map values will not be read
                    addToCache(partOffset) { _, _ ->
                        // Ignore reading and return
                    }
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                val mapDefinition = definition as? IsMapDefinition<Any, Any, IsPropertyContext>
                    ?: throw TypeException("Definition $definition should be a MapDefinition")
                @Suppress("UNCHECKED_CAST")
                val mapReference = reference as CanContainMapItemReference<*, *, *>
                val keyDefinition = mapDefinition.keyDefinition

                val keySize = initIntByVar { qualifierReader(offset++) }
                val key = keyDefinition.readStorageBytes(keySize) { qualifierReader(offset++) }

                // Create map Item adder
                val mapItemAdder: AddValue = { value ->
                    addValueToOutput(index, Pair(key, value))
                }

                val valueReference = mapDefinition.valueRef(key, mapReference)

                // Begin to read map value
                if (qualifierLength <= offset) {
                    val value = readValueFromStorage(Value, valueReference)

                    when {
                        value == null ->
                            // Ensure that next map values will not be read because they are deleted
                            addToCache(offset - 1) { _, _ ->
                                // Ignore reading and return
                            }
                        value != Unit -> mapItemAdder(value)
                        else -> null
                    }
                } else {
                    readComplexWithSubDefinition(
                        qualifierReader,
                        qualifierLength,
                        offset,
                        readValueFromStorage,
                        mapDefinition,
                        mapDefinition.valueDefinition,
                        select,
                        valueReference,
                        addToCache,
                        mapItemAdder
                    )
                }
            }
        }
        TYPE -> {
            @Suppress("UNCHECKED_CAST")
            val typedDefinition = definition as? IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>
                ?: throw TypeException("Definition($index) $definition should be a TypedDefinition")

            typedDefinition.readComplexTypedValue(
                index,
                qualifierReader,
                qualifierLength,
                offset,
                readValueFromStorage,
                select,
                reference,
                addToCache,
                { addValueToOutput(index, it) })
        }
    }
}

/** Read embedded values into Values object */
private fun <P : IsValuesPropertyDefinitions> readEmbeddedValues(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueReader,
    definition: IsEmbeddedDefinition<*, *>,
    parentReference: IsPropertyReference<*, *, *>,
    select: IsPropRefGraph<P>?,
    addToCache: CacheProcessor,
    addValueToOutput: AddValue
) {
    @Suppress("UNCHECKED_CAST")
    val dataModel = definition.dataModel as IsDataModelWithValues<*, out IsValuesPropertyDefinitions, *>
    val values = dataModel.values { MutableValueItems() }

    addValueToOutput(values)

    val valuesItemAdder: AddToValues = { i, value ->
        values.add(i, value)
    }

    // If select is Graph then resolve sub graph.
    // Otherwise, it is null or is property itself so needs to be completely selected thus set as null.
    val specificSelect = if (select is IsPropRefGraph<*>) {
        @Suppress("UNCHECKED_CAST")
        select as IsPropRefGraph<IsValuesPropertyDefinitions>
    } else null

    addToCache(offset - 1) { qr, l ->
        dataModel.readQualifier(
            qr, l,
            offset,
            specificSelect,
            parentReference,
            valuesItemAdder,
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
        valuesItemAdder,
        readValueFromStorage,
        addToCache
    )
}

/** Read a typed value */
private fun readTypedValue(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueReader,
    valueDefinition: IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
    select: IsPropRefGraph<*>?,
    parentReference: IsPropertyReference<*, *, *>,
    addToCache: CacheProcessor,
    addValueToOutput: AddValue,
    typeToCheck: IndexedEnum? = null
) {
    if (qualifierLength <= offset) {
        readValueFromStorage(Value, parentReference)?.let {
            // Pass type to check
            addToCache(offset - 1) { qr, ql ->
                readTypedValue(
                    qualifierReader = qr,
                    qualifierLength = ql,
                    offset = offset,
                    readValueFromStorage = readValueFromStorage,
                    valueDefinition = valueDefinition,
                    select = select,
                    parentReference = parentReference,
                    addToCache = addToCache,
                    addValueToOutput = addValueToOutput,
                    typeToCheck = (it as TypedValue<*, *>).type
                )
            }

            addValueToOutput(it)
        }
    } else {
        var qIndex = offset
        initUIntByVarWithExtraInfo({ qualifierReader(qIndex++) }) { typeIndex, _ ->
            typeToCheck?.let {
                // Skip values if type does not check out
                if (typeToCheck.index != typeIndex) {
                    return@initUIntByVarWithExtraInfo
                }
            }

            valueDefinition.readComplexTypedValue(
                typeIndex,
                qualifierReader,
                qualifierLength,
                qIndex,
                readValueFromStorage,
                select,
                parentReference,
                addToCache,
                addValueToOutput
            )
        }
    }
}

/** Read a complex Typed value from qualifier */
private fun IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>.readComplexTypedValue(
    index: UInt,
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueReader,
    select: IsPropRefGraph<*>?,
    reference: IsPropertyReference<*, *, *>?,
    addToCache: CacheProcessor,
    addValueToOutput: AddValue
) {
    val definition = this.definition(index)
        ?: throw DefNotFoundException("No definition for $index in $this")
    val type = this.typeEnum.resolve(index)
        ?: throw DefNotFoundException("Unknown type $index for $this")
    val typedValueReference = TypedValueReference(type, this, reference as CanHaveComplexChildReference<*, *, *, *>?)

    val addMultiTypeToOutput: AddValue = { addValueToOutput(TypedValue(type, it)) }

    if (qualifierLength <= offset) {
        val value = readValueFromStorage(Embed, typedValueReference)

        if (value == null) {
            // Ensure that next values will not be read because Values are deleted
            addToCache(offset - 1) { _, _ -> }
        }
        // Don't process further
        return
    }

    readComplexWithSubDefinition(
        qualifierReader,
        qualifierLength,
        offset,
        readValueFromStorage,
        this,
        definition,
        select,
        typedValueReference,
        addToCache,
        addMultiTypeToOutput
    )
}

/** Read values for a sub definition (used in map values and multi types) */
private fun readComplexWithSubDefinition(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offset: Int,
    readValueFromStorage: ValueReader,
    parentDefinition: IsPropertyDefinition<*>,
    valueDefinition: IsSubDefinition<out Any, Nothing>,
    select: IsPropRefGraph<*>?,
    reference: IsPropertyReference<*, *, *>,
    addToCache: CacheProcessor,
    valueAdder: AddValue
) = when (valueDefinition) {
    is IsMultiTypeDefinition<*, *, *> -> {
        @Suppress("UNCHECKED_CAST")
        readTypedValue(
            qualifierReader,
            qualifierLength,
            offset,
            readValueFromStorage,
            valueDefinition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
            select,
            reference,
            addToCache,
            valueAdder
        )
    }
    is IsEmbeddedDefinition<*, *> -> {
        readEmbeddedValues(
            qualifierReader,
            qualifierLength,
            offset,
            readValueFromStorage,
            valueDefinition,
            reference,
            select,
            addToCache,
            valueAdder
        )
    }
    is IsListDefinition<*, *> -> {
        readQualifierOfType(
            qualifierReader = qualifierReader,
            qualifierLength = qualifierLength,
            currentOffset = offset + 1,
            partOffset = offset,
            definition = valueDefinition,
            index = 0u, // Is ignored by addValueToOutput
            refStoreType = LIST,
            select = select,
            reference = reference,
            addValueToOutput = { _, value -> valueAdder(value) },
            readValueFromStorage = readValueFromStorage,
            addToCache = addToCache
        )
    }
    is IsSetDefinition<*, *> -> {
        readQualifierOfType(
            qualifierReader = qualifierReader,
            qualifierLength = qualifierLength,
            currentOffset = offset + 1,
            partOffset = offset,
            definition = valueDefinition,
            index = 0u, // Is ignored by addValueToOutput
            refStoreType = SET,
            select = select,
            reference = reference,
            addValueToOutput = { _, value -> valueAdder(value) },
            readValueFromStorage = readValueFromStorage,
            addToCache = addToCache
        )
    }
    is IsMapDefinition<*, *, *> -> {
        readQualifierOfType(
            qualifierReader = qualifierReader,
            qualifierLength = qualifierLength,
            currentOffset = offset + 1,
            partOffset = offset,
            definition = valueDefinition,
            index = 0u, // Is ignored by addValueToOutput
            refStoreType = MAP,
            select = select,
            reference = reference,
            addValueToOutput = { _, value -> valueAdder(value) },
            readValueFromStorage = readValueFromStorage,
            addToCache = addToCache
        )
    }
    else -> throw StorageException("Can only use Embedded/MultiType/List/Set/Map as complex value type in $parentDefinition")
}
