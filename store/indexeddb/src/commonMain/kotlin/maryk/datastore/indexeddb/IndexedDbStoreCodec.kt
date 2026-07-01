package maryk.datastore.indexeddb

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.readValue
import maryk.lib.extensions.compare.matchesRangePart

internal data class IndexedDbRecordMeta(
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: Boolean,
)

internal fun encodeRecordMeta(meta: IndexedDbRecordMeta): ByteArray {
    val bytes = ByteArray(17)
    var index = 0
    meta.firstVersion.writeBytes({ bytes[index++] = it })
    meta.lastVersion.writeBytes({ bytes[index++] = it })
    bytes[index] = if (meta.isDeleted) 1 else 0
    return bytes
}

internal fun decodeRecordMeta(bytes: ByteArray): IndexedDbRecordMeta {
    require(bytes.size >= 17) { "Invalid IndexedDB meta size ${bytes.size}" }

    var index = 0
    val firstVersion = initULong(reader = { bytes[index++] })
    val lastVersion = initULong(reader = { bytes[index++] })
    val isDeleted = bytes[index] == 1.toByte()
    return IndexedDbRecordMeta(firstVersion, lastVersion, isDeleted)
}

internal fun encodeStorageValue(
    type: StorageTypeEnum<IsPropertyDefinition<*>>,
    definition: IsPropertyDefinition<*>,
    value: Any,
): ByteArray = when (type) {
    ObjectDelete -> byteArrayOf(TypeIndicator.DeletedIndicator.byte)
    Value -> Value.castDefinition(definition).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
    ListSize, SetSize, MapSize -> (value as Int).toVarBytes()
    Embed -> byteArrayOf(TypeIndicator.EmbedIndicator.byte, 1)
    TypeValue -> encodeTypedStorageValue(value, definition)
}

private fun encodeTypedStorageValue(
    value: Any,
    definition: IsPropertyDefinition<*>,
): ByteArray {
    val properValue = if (value is MultiTypeEnum<*>) value.invoke(Unit) else value
    val typedValue = properValue as TypedValue<TypeEnum<*>, *>
    val typeDefinition = TypeValue.castDefinition(definition)
    val bytes = mutableListOf<Byte>()

    typedValue.type.index.writeVarIntWithExtraInfo(
        if (typedValue.value == Unit) {
            TypeIndicator.ComplexTypeIndicator.byte
        } else {
            TypeIndicator.SimpleTypeIndicator.byte
        }
    ) { bytes.add(it) }

    if (typedValue.value != Unit) {
        @Suppress("UNCHECKED_CAST")
        val valueDefinition = typeDefinition.definition(typedValue.type) as IsSimpleValueDefinition<Any, *>
        valueDefinition.writeStorageBytes(typedValue.value) { bytes.add(it) }
    }

    return bytes.toByteArray()
}

internal suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readRecord(
    dataModel: DM,
    keyStoreName: String,
    tableStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    val meta = get(keyStoreName, keyBytes)?.let(::decodeRecordMeta) ?: return null
    val values = readCurrentValues(dataModel, tableStoreName, keyBytes, select, decryptValue) ?: return null

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

internal suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readCurrentValues(
    dataModel: DM,
    tableStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): Values<DM>? {
    if (select != null && select.properties.isEmpty()) {
        return dataModel.emptyValues()
    }

    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    val rows = scan(
        storeName = tableStoreName,
        startKey = rowKeyPrefix,
        endKey = rowKeyPrefix.nextKeyPrefixUpperBound(),
        includeEnd = false,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }

    if (rows.isEmpty()) {
        return null
    }

    return decodeStorageRowsToValues(
        dataModel = dataModel,
        rows = rows.map { (rowKey, rowValue) ->
            tableQualifierFromRowKey(rowKey, keyBytes) to decryptValue(rowValue)
        },
        select = select,
    )
}

internal fun <DM : IsRootDataModel> decodeStorageRowsToValues(
    dataModel: DM,
    rows: List<Pair<ByteArray, ByteArray>>,
    select: RootPropRefGraph<DM>?,
): Values<DM>? {
    if (select != null && select.properties.isEmpty()) {
        return dataModel.emptyValues()
    }

    var rowIndex = 0
    var currentValue: ByteArray? = null

    val values = dataModel.readStorageToValues(
        getQualifier = { processQualifier ->
            val (rowKey, rowValue) = rows.getOrNull(rowIndex++) ?: return@readStorageToValues false
            currentValue = rowValue
            processQualifier({ rowKey[it] }, rowKey.size)
            true
        },
        select = select,
        processValue = { storageType, reference ->
            val valueBytes = currentValue ?: return@readStorageToValues null
            when (storageType) {
                ObjectDelete -> null
                Embed -> Unit
                Value -> decodeStorageValue(reference, valueBytes)
                ListSize, SetSize, MapSize -> {
                    var index = 0
                    initIntByVar { valueBytes[index++] }
                }
                TypeValue -> error("TypeValue is encoded as a regular storage value")
            }
        }
    )

    if (values.size == 0 && (select == null || select.properties.isNotEmpty())) {
        return null
    }

    return values
}

internal fun decodeStorageValue(
    reference: IsPropertyReferenceForCache<*, *>,
    valueBytes: ByteArray,
): Any? {
    var index = 0
    val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
        ?: reference.propertyDefinition
    return readValue(definition, { valueBytes[index++] }) { valueBytes.size - index }
}

internal fun createTableRowKey(keyBytes: ByteArray, qualifier: ByteArray): ByteArray {
    val keyPrefix = createObjectRowKeyPrefix(keyBytes)
    val combined = ByteArray(keyPrefix.size + qualifier.size)
    keyPrefix.copyInto(combined, endIndex = keyPrefix.size)
    qualifier.copyInto(combined, destinationOffset = keyPrefix.size)
    return combined
}

internal fun createObjectRowKeyPrefix(keyBytes: ByteArray): ByteArray {
    val keyLength = keyBytes.size.toVarBytes()
    val prefix = ByteArray(keyLength.size + keyBytes.size)
    keyLength.copyInto(prefix, endIndex = keyLength.size)
    keyBytes.copyInto(prefix, destinationOffset = keyLength.size)
    return prefix
}

internal fun tableQualifierFromRowKey(rowKey: ByteArray, keyBytes: ByteArray): ByteArray =
    rowKey.copyOfRange(createObjectRowKeyPrefix(keyBytes).size, rowKey.size)

internal fun objectKeyBytesFromScopedRowKey(rowKey: ByteArray): ByteArray {
    var index = 0
    val keySize = initIntByVar { rowKey[index++] }
    return rowKey.copyOfRange(index, index + keySize)
}

private fun ByteArray.nextKeyPrefixUpperBound(): ByteArray? {
    val next = this.copyOf()
    for (index in next.lastIndex downTo 0) {
        if (next[index] != 0xFF.toByte()) {
            next[index]++
            return next
        }
    }
    return null
}
