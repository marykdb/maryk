@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.CompleteReferenceType
import maryk.core.properties.references.CompleteReferenceType.DELETE
import maryk.core.properties.references.CompleteReferenceType.LIST
import maryk.core.properties.references.CompleteReferenceType.MAP
import maryk.core.properties.references.CompleteReferenceType.SET
import maryk.core.properties.references.CompleteReferenceType.VALUE
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.types.TypedValue
import maryk.core.values.AbstractValues
import maryk.core.values.AnyAbstractValues

@kotlin.Suppress("unused")
sealed class StorageTypeEnum<T: IsPropertyDefinition<*>>(val referenceType: CompleteReferenceType) {
    object ObjectDelete: StorageTypeEnum<IsPropertyDefinition<Boolean>>(DELETE)
    object Value: StorageTypeEnum<IsSimpleValueDefinition<Any, IsPropertyContext>>(CompleteReferenceType.VALUE)
    object ListSize: StorageTypeEnum<IsListDefinition<Any, IsPropertyContext>>(LIST)
    object SetSize: StorageTypeEnum<IsSetDefinition<Any, IsPropertyContext>>(SET)
    object MapSize: StorageTypeEnum<IsMapDefinition<Any, Any, IsPropertyContext>>(MAP)
    object TypeValue: StorageTypeEnum<IsMultiTypeDefinition<IndexedEnum<Any>, IsPropertyContext>>(VALUE)

    @Suppress("UNCHECKED_CAST")
    fun castDefinition(definition: IsPropertyDefinition<*>) = definition as T
}

typealias ValueWriter<T> = (StorageTypeEnum<T>, ByteArray, T, Any) -> Unit
private typealias QualifierWriter = ((Byte) -> Unit) -> Unit

/**
 * Walk Values and process storable values.
 * Pass [valueWriter] to process values
 */
fun <DM: IsDataModel<P>, P: AbstractPropertyDefinitions<*>> AbstractValues<*, DM, P>.writeToStorage(
    valueWriter: ValueWriter<IsPropertyDefinition<*>>
) = this.writeToStorage(0, null, valueWriter)

/**
 * Walk Values and process storable values.
 * [qualifierCount], [qualifierWriter] define the count and writer for any parent property
 * Pass [valueWriter] to process values
 */
private fun <DM: IsDataModel<P>, P: AbstractPropertyDefinitions<*>> AbstractValues<*, DM, P>.writeToStorage(
    qualifierCount: Int = 0,
    qualifierWriter: QualifierWriter? = null,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>
) {
    for ((index, value) in this.values) {
        val definition = this.dataModel.properties[index]!!
        writeValue(definition.index, qualifierCount, qualifierWriter, definition.definition, value, valueWriter)
    }
}

/**
 * Process a single value with [valueWriter] at [index] with [definition] and [value]
 * [qualifierLength], [qualifierWriter] define the count and writer for any parent property
 * If index is -1, this value has no index.
 */
@Suppress("UNCHECKED_CAST")
private fun <T: IsPropertyDefinition<*>> writeValue(
    index: Int,
    qualifierLength: Int,
    qualifierWriter: QualifierWriter? = null,
    definition: T,
    value: Any,
    valueWriter: ValueWriter<T>
) {
    when (value) {
        is List<*> -> {
            val listQualifierWriter = createQualifierWriter(qualifierWriter, index, ReferenceType.LIST)
            val listQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process List Count
            valueWriter(ListSize as StorageTypeEnum<T>, writeQualifier(listQualifierCount, listQualifierWriter), definition, value.size) // for list count

            // Process List values
            val listValueDefinition = (definition as ListDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
            for ((listIndex, listItem) in (value as List<Any>).withIndex()) {
                val listValueQualifierWriter: QualifierWriter = { writer ->
                    listQualifierWriter.invoke(writer)
                    listIndex.toUInt().writeBytes(writer, 4)
                }
                writeValue(
                    -1, listQualifierCount + 4, listValueQualifierWriter,
                    listValueDefinition,
                    listItem,
                    valueWriter as ValueWriter<IsValueDefinition<*, *>>
                )
            }
        }
        is Set<*> -> {
            val setQualifierWriter = createQualifierWriter(qualifierWriter, index, ReferenceType.SET)
            val setQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process Set Count
            valueWriter(SetSize as StorageTypeEnum<T>, writeQualifier(setQualifierCount, setQualifierWriter), definition, value.size) // for set count

            // Process Set Values
            val setValueDefinition = (definition as SetDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
            val set = value as Set<Any>
            for (setItem in set) {
                val setValueQualifierWriter: QualifierWriter = { writer ->
                    setQualifierWriter.invoke(writer)
                    setValueDefinition.writeStorageBytes(setItem, writer)
                }
                writeValue(
                    -1,
                    setQualifierCount + setValueDefinition.calculateStorageByteLength(setItem),
                    setValueQualifierWriter,
                    setValueDefinition,
                    setItem,
                    valueWriter as ValueWriter<IsValueDefinition<*, *>>
                )
            }
        }
        is Map<*, *> -> {
            val mapQualifierWriter = createQualifierWriter(qualifierWriter, index, ReferenceType.MAP)
            val mapQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process Map Count
            valueWriter(MapSize as StorageTypeEnum<T>, writeQualifier(mapQualifierCount, mapQualifierWriter), definition, value.size)

            // Process Map Values
            val mapDefinition = (definition as IsMapDefinition<Any, *, *>)
            val map = value as Map<Any, Any>
            for ((key, mapValue) in map) {
                val keyByteSize = mapDefinition.keyDefinition.calculateStorageByteLength(key)
                val keyByteCountSize = keyByteSize.calculateVarByteLength()

                val mapValueQualifierWriter: QualifierWriter = { writer ->
                    mapQualifierWriter.invoke(writer)
                    keyByteSize.writeVarBytes(writer)

                    mapDefinition.keyDefinition.writeStorageBytes(key, writer)
                }
                val mapValueQualifierLength = mapQualifierCount + keyByteSize + keyByteCountSize

                // Write complex map existence indicator
                if (mapDefinition.valueDefinition is EmbeddedValuesDefinition<*, *>) {
                    // Write parent value with Unit so it knows this one is not deleted. So possible lingering old types are not read.
                    val qualifier = writeQualifier(mapValueQualifierLength, mapValueQualifierWriter)
                    valueWriter(TypeValue as StorageTypeEnum<T>, qualifier, definition, Unit)
                }

                writeValue(
                    -1, mapValueQualifierLength, mapValueQualifierWriter,
                    mapDefinition.valueDefinition,
                    mapValue,
                    valueWriter as ValueWriter<IsSubDefinition<*, *>>
                )
            }
        }
        is AbstractValues<*, *, *> -> {
            val indexWriter = if (index == -1) qualifierWriter else createQualifierWriter(qualifierWriter, index, ReferenceType.VALUE)
            val abstractValuesQualifierCount = if (index == -1) qualifierLength else qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            (value as AnyAbstractValues).writeToStorage(abstractValuesQualifierCount, indexWriter, valueWriter as ValueWriter<IsPropertyDefinition<*>>)
        }
        is TypedValue<*, *> -> {
            val multiDefinition = definition as MultiTypeDefinition<*, *>
            val valueDefinition = multiDefinition.definitionMap[value.type] as IsPropertyDefinition<Any>

            val valueQualifierWriter = if (index > -1) {
                createQualifierWriter(qualifierWriter, index, ReferenceType.VALUE)
            } else qualifierWriter
            val valueQualifierSize = if (index > -1) {
                qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            } else qualifierLength

            if (valueDefinition is IsSimpleValueDefinition<*, *>) {
                val qualifier = writeQualifier(valueQualifierSize, valueQualifierWriter)
                valueWriter(TypeValue as StorageTypeEnum<T>, qualifier, definition, value)
            } else {
                val qualifierTypeLength = valueQualifierSize + value.type.index.calculateVarIntWithExtraInfoByteSize()
                val qualifierTypeWriter = createQualifierWriter(valueQualifierWriter, value.type.index, ReferenceType.TYPE)

                // Write parent value to contain current type. So possible lingering old types are not read.
                val qualifier = writeQualifier(valueQualifierSize, valueQualifierWriter)
                valueWriter(TypeValue as StorageTypeEnum<T>, qualifier, definition, TypedValue(value.type as IndexedEnum<IndexedEnum<*>>, Unit))

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
        else -> {
            val qualifier = if (index > -1) {
                writeQualifier(
                    qualifierLength + index.calculateVarIntWithExtraInfoByteSize(),
                    createQualifierWriter(qualifierWriter, index, ReferenceType.VALUE)
                )
            } else writeQualifier(qualifierLength, qualifierWriter)

            valueWriter(Value as StorageTypeEnum<T>, qualifier, definition, value)
        }
    }
}

/**
 * Create a qualifier writer which writes an [index] and [referenceType]
 * Also first writes with a [parentQualifierWriter] if not null
 */
private fun createQualifierWriter(
    parentQualifierWriter: QualifierWriter?,
    index: Int,
    referenceType: ReferenceType
): QualifierWriter = { writer ->
    parentQualifierWriter?.invoke(writer)
    index.writeVarIntWithExtraInfo(referenceType.value, writer)
}

/**
 * Write a specific qualifier with passed [qualifierLength] and [qualifierWriter]
 */
private fun writeQualifier(
    qualifierLength: Int,
    qualifierWriter: QualifierWriter?
): ByteArray {
    return ByteArray(qualifierLength).also { bytes ->
        var i = 0
        qualifierWriter?.invoke {
            bytes[i++] = it
        }
    }
}
