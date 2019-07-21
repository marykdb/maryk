package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.types.TypedValue
import maryk.datastore.rocksdb.processors.COMPLEX_TYPE_INDICATOR
import maryk.datastore.rocksdb.processors.DELETED_INDICATOR
import maryk.datastore.rocksdb.processors.EMBED_INDICATOR
import maryk.datastore.rocksdb.processors.NO_TYPE_INDICATOR
import maryk.datastore.rocksdb.processors.SIMPLE_TYPE_INDICATOR

/**
 * Read a Value storage type property from [reader] with [definition] and get amount to read
 * from [valueBytesLeft]
 * These values have an indicator byte to signal they are a normal value/multi type or embed
 */
internal fun readValue(
    definition: IsPropertyDefinition<out Any>?,
    reader: () -> Byte,
    valueBytesLeft: () -> Int
): Any? {
    return initUIntByVarWithExtraInfo(reader) { type, indicatorByte ->
        when (indicatorByte) {
            NO_TYPE_INDICATOR -> {
                val valueDefinition = if (definition is IsCollectionDefinition<*, *, *, *>) {
                    definition.valueDefinition
                } else definition
                Value.castDefinition(valueDefinition).readStorageBytes(
                    valueBytesLeft(),
                    reader
                )
            }
            SIMPLE_TYPE_INDICATOR -> {
                val typeDefinition =
                    TypeValue.castDefinition(definition)
                val valueDefinition =
                    typeDefinition.definition(type) as IsSimpleValueDefinition<*, *>
                val typeEnum = typeDefinition.typeEnum.resolve(type) ?:
                    throw StorageException("Unknown type $type for $typeDefinition")
                val value = valueDefinition.readStorageBytes(valueBytesLeft(), reader)
                TypedValue(typeEnum, value)
            }
            COMPLEX_TYPE_INDICATOR -> {
                val typeDefinition =
                    TypeValue.castDefinition(definition)
                val typeEnum = typeDefinition.typeEnum.resolve(type) ?:
                    throw StorageException("Unknown type $type for $typeDefinition")
                TypedValue(typeEnum, Unit)
            }
            EMBED_INDICATOR -> {
                // Todo: skip deleted?
                Unit
            }
            DELETED_INDICATOR -> null
            else -> throw StorageException("Unknown Value type $indicatorByte in store")
        }
    }
}
