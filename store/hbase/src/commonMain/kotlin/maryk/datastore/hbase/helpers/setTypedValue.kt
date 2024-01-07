package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import org.apache.hadoop.hbase.client.Put

internal fun setTypedValue(
    value: Any,
    definition: IsPropertyDefinition<*>,
    put: Put,
    reference: ByteArray,
    qualifiersToKeep: MutableList<ByteArray>? = null,
    shouldWrite: ((referenceBytes: ByteArray, valueBytes: ByteArray) -> Boolean)? = null,
) {
    val properValue = if (value is MultiTypeEnum<*>) {
        TypedValue(value, Unit)
    } else {
        value
    }
    val typedValue = properValue as TypedValue<TypeEnum<*>, *>
    val typeDefinition = TypeValue.castDefinition(definition)

    var index = 0
    if (typedValue.value == Unit) {
        val valueBytes = ByteArray(typedValue.type.index.calculateVarIntWithExtraInfoByteSize())
        typedValue.type.index.writeVarIntWithExtraInfo(TypeIndicator.ComplexTypeIndicator.byte) { valueBytes[index++] = it }

        qualifiersToKeep?.add(reference)
        if (shouldWrite?.invoke(reference, valueBytes) != false) {
            put.addColumn(dataColumnFamily, reference, valueBytes)
        }
    } else {
        val typeValueDefinition = typeDefinition.definition(typedValue.type) as IsSimpleValueDefinition<Any, *>
        val valueBytes = ByteArray(
            typedValue.type.index.calculateVarIntWithExtraInfoByteSize() +
                typeValueDefinition.calculateStorageByteLength(typedValue.value)
        )
        val writer: (Byte) -> Unit = { valueBytes[index++] = it }

        typedValue.type.index.writeVarIntWithExtraInfo(TypeIndicator.SimpleTypeIndicator.byte, writer)
        typeValueDefinition.writeStorageBytes(typedValue.value, writer)

        qualifiersToKeep?.add(reference)
        if (shouldWrite?.invoke(reference, valueBytes) != false) {
            put.addColumn(dataColumnFamily, reference, valueBytes)
        }
    }
}
