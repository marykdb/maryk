package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.COMPLEX_TYPE_INDICATOR
import maryk.datastore.rocksdb.processors.SIMPLE_TYPE_INDICATOR

internal fun setTypedValue(
    value: Any,
    definition: IsPropertyDefinition<*>,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    reference: ByteArray,
    versionBytes: ByteArray
) {
    val typedValue = value as TypedValue<TypeEnum<*>, *>
    val typeDefinition = TypeValue.castDefinition(definition)

    var index = 0
    if (typedValue.value == Unit) {
        val valueBytes = ByteArray(value.type.index.calculateVarIntWithExtraInfoByteSize())
        typedValue.type.index.writeVarIntWithExtraInfo(COMPLEX_TYPE_INDICATOR) { valueBytes[index++] = it }

        setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
    } else {
        val typeValueDefinition = typeDefinition.definition(typedValue.type) as IsSimpleValueDefinition<Any, *>
        val valueBytes = ByteArray(
            typedValue.type.index.calculateVarIntWithExtraInfoByteSize() +
                typeValueDefinition.calculateStorageByteLength(typedValue.value)
        )
        val writer: (Byte) -> Unit = { valueBytes[index++] = it }

        typedValue.type.index.writeVarIntWithExtraInfo(SIMPLE_TYPE_INDICATOR, writer)
        typeValueDefinition.writeStorageBytes(typedValue.value, writer)

        setValue(
            transaction,
            columnFamilies,
            key,
            reference,
            versionBytes,
            valueBytes
        )
    }
}
