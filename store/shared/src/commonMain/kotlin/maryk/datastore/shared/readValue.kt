package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.invoke

/**
 * Read a Value storage type property from [reader] with [definition] and get amount to read
 * from [valueBytesLeft]
 * These values have an indicator byte to signal they are a normal value/multi type or embed
 */
fun readValue(
    definition: IsPropertyDefinition<out Any>?,
    reader: () -> Byte,
    valueBytesLeft: () -> Int
): Any? {
    return initUIntByVarWithExtraInfo(reader) { type, indicatorByte ->
        when (indicatorByte) {
            TypeIndicator.NoTypeIndicator.byte -> {
                val valueDefinition = if (definition is IsCollectionDefinition<*, *, *, *>) {
                    definition.valueDefinition
                } else if (definition is IsDefinitionWrapper<*, *, *, *>) {
                    definition.definition
                } else {
                    definition
                }
                Value.castDefinition(valueDefinition).readStorageBytes(
                    valueBytesLeft(),
                    reader
                )
            }
            TypeIndicator.SimpleTypeIndicator.byte -> {
                fun resolveType(enumDef: IsIndexedEnumDefinition<*>): MultiTypeEnum<*> =
                    enumDef.resolve(type) as? MultiTypeEnum<*> ?: throw StorageException("Unknown type $type for $enumDef")

                val typeEnum = when (definition) {
                    is IsMultiTypeDefinition<*, *, *> -> resolveType(TypeValue.castDefinition(definition).typeEnum)
                    is MultiTypeEnumDefinition<*> -> resolveType(definition)
                    else -> throw StorageException("Unknown type $type for $definition")
                }
                val valueDefinition = typeEnum.definition as? IsSimpleValueDefinition<*, *>
                    ?: throw StorageException("Unknown type $type for $typeEnum. Was it added to the EnumDefinition?")
                val value = valueDefinition.readStorageBytes(valueBytesLeft(), reader)
                typeEnum.invoke(value)
            }
            TypeIndicator.ComplexTypeIndicator.byte -> {
                fun resolveType(enumDef: IsIndexedEnumDefinition<*>): TypeEnum<*> =
                    enumDef.resolve(type) as? TypeEnum<*> ?: throw StorageException("Unknown type $type for $enumDef")

                // Change output a bit on what is expected by given definition
                // The multi type def is useful for deletes while the enum def is used for TypeReferences and equals checks
                when (definition) {
                    is IsMultiTypeDefinition<*, *, *> -> resolveType(TypeValue.castDefinition(definition).typeEnum)
                    is MultiTypeEnumDefinition<*> -> resolveType(definition)
                    else -> throw StorageException("Unknown type $type for $definition")
                }
            }
            TypeIndicator.EmbedIndicator.byte -> {
                // Todo: skip deleted?
                Unit
            }
            TypeIndicator.DeletedIndicator.byte -> null
            else -> throw StorageException("Unknown Value type $indicatorByte in store")
        }
    }
}
