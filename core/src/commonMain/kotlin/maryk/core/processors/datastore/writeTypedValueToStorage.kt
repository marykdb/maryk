package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TypedValue

/** Write a complete [typedValue] defined by [definition] with [qualifierWriter] of [qualifierSize] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeTypedValueToStorage(
    qualifierSize: Int,
    qualifierWriter: (((Byte) -> Unit) -> Unit)?,
    valueWriter: ValueWriter<T>,
    definition: T,
    typedValue: TypedValue<*, *>
) {
    val multiDefinition = definition as IsMultiTypeDefinition<*, *, *>
    val valueDefinition = multiDefinition.definitionMap[typedValue.type] as IsPropertyDefinition<Any>

    if (valueDefinition is IsSimpleValueDefinition<*, *>) {
        val qualifier = writeQualifier(qualifierSize, qualifierWriter)
        valueWriter(TypeValue as StorageTypeEnum<T>, qualifier, definition, typedValue)
    } else {
        val qualifierTypeLength = qualifierSize + typedValue.type.index.calculateVarIntWithExtraInfoByteSize()
        val qualifierTypeWriter = createQualifierWriter(
            qualifierWriter,
            typedValue.type.index,
            TYPE
        )

        // Write parent value to contain current type. So possible lingering old types are not read.
        val qualifier = writeQualifier(qualifierSize, qualifierWriter)
        valueWriter(
            TypeValue as StorageTypeEnum<T>,
            qualifier,
            definition,
            TypedValue(
                typedValue.type,
                Unit
            )
        )

        // write sub value(s)
        writeValue(
            null,
            qualifierTypeLength,
            qualifierTypeWriter,
            valueDefinition,
            typedValue.value,
            valueWriter as ValueWriter<IsPropertyDefinition<Any>>
        )
    }
}
