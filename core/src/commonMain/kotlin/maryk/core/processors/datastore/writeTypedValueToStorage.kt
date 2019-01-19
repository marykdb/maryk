package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TypedValue

/** Write a complete [typedValue] referenced by [reference] to storage with [valueWriter]. */
fun writeTypedValueToStorage(
    reference: MultiTypePropertyReference<*, *, *, *>,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>,
    typedValue: TypedValue<*, *>
) {
    writeTypedValueToStorage(reference::writeStorageBytes, reference.calculateStorageByteLength(), valueWriter, reference.propertyDefinition.definition, typedValue)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>> writeTypedValueToStorage(
    qualifierWriter: (((Byte) -> Unit) -> Unit)?,
    qualifierSize: Int,
    valueWriter: ValueWriter<T>,
    definition: T,
    value: TypedValue<*, *>
) {
    val multiDefinition = definition as MultiTypeDefinition<*, *>
    val valueDefinition = multiDefinition.definitionMap[value.type] as IsPropertyDefinition<Any>

    if (valueDefinition is IsSimpleValueDefinition<*, *>) {
        val qualifier = writeQualifier(qualifierSize, qualifierWriter)
        valueWriter(TypeValue as StorageTypeEnum<T>, qualifier, definition, value)
    } else {
        val qualifierTypeLength = qualifierSize + value.type.index.calculateVarIntWithExtraInfoByteSize()
        val qualifierTypeWriter = createQualifierWriter(
            qualifierWriter,
            value.type.index,
            TYPE
        )

        // Write parent value to contain current type. So possible lingering old types are not read.
        val qualifier = writeQualifier(qualifierSize, qualifierWriter)
        valueWriter(
            TypeValue as StorageTypeEnum<T>,
            qualifier,
            definition,
            TypedValue(
                value.type as IndexedEnum<IndexedEnum<*>>,
                Unit
            )
        )

        // write sub value(s)
        writeValue(
            -1,
            qualifierTypeLength,
            qualifierTypeWriter,
            valueDefinition,
            value.value,
            valueWriter as ValueWriter<IsPropertyDefinition<Any>>
        )
    }
}
