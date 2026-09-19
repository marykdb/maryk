package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.TypeIndicator

internal fun setTypedValue(
    value: Any,
    definition: IsPropertyDefinition<*>,
    transaction: Transaction,
    directories: IsTableDirectories,
    key: maryk.core.properties.types.Key<*>,
    reference: ByteArray,
    versionBytes: ByteArray,
    qualifiersToKeep: MutableList<ByteArray>? = null,
    shouldWrite: ((referenceBytes: ByteArray, valueBytes: ByteArray) -> Boolean)? = null,
) {
    val properValue = if (value is MultiTypeEnum<*>) {
        value.invoke(Unit)
    } else {
        value
    }

    @Suppress("UNCHECKED_CAST")
    val typedValue = properValue as TypedValue<TypeEnum<*>, *>
    val typeDefinition = TypeValue.castDefinition(definition)

    var index = 0
    qualifiersToKeep?.add(reference)

    if (typedValue.value == Unit) {
        // Complex type without an inline value
        val valueBytes = ByteArray(typedValue.type.index.calculateVarIntWithExtraInfoByteSize())
        typedValue.type.index.writeVarIntWithExtraInfo(TypeIndicator.ComplexTypeIndicator.byte) {
            valueBytes[index++] = it
        }

        if (shouldWrite?.invoke(reference, valueBytes) != false) {
            setValue(
                transaction,
                directories,
                key.bytes,
                reference,
                versionBytes,
                valueBytes
            )
        }
    } else {
        // Simple typed value: [type index varint + simple value bytes]
        val typeValueDefinition = typeDefinition.definition(typedValue.type) as IsSimpleValueDefinition<Any, *>
        val valueBytes = ByteArray(
            typedValue.type.index.calculateVarIntWithExtraInfoByteSize() +
                typeValueDefinition.calculateStorageByteLength(typedValue.value)
        )
        val writer: (Byte) -> Unit = { valueBytes[index++] = it }

        typedValue.type.index.writeVarIntWithExtraInfo(
            TypeIndicator.SimpleTypeIndicator.byte,
            writer
        )
        typeValueDefinition.writeStorageBytes(typedValue.value, writer)

        if (shouldWrite?.invoke(reference, valueBytes) != false) {
            setValue(
                transaction,
                directories,
                key.bytes,
                reference,
                versionBytes,
                valueBytes
            )
        }
    }
}
