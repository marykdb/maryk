@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsDataModel
import maryk.core.processors.datastore.StorageTypeEnum.ListCount
import maryk.core.processors.datastore.StorageTypeEnum.MapCount
import maryk.core.processors.datastore.StorageTypeEnum.SetCount
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.types.TypedValue
import maryk.core.values.AbstractValues
import maryk.core.values.AnyAbstractValues

@kotlin.Suppress("unused")
sealed class StorageTypeEnum<T: IsPropertyDefinition<*>>(val referenceType: ReferenceType) {
    object Value: StorageTypeEnum<IsSimpleValueDefinition<Any, IsPropertyContext>>(VALUE)
    object ListCount: StorageTypeEnum<IsListDefinition<Any, IsPropertyContext>>(LIST)
    object SetCount: StorageTypeEnum<IsSetDefinition<Any, IsPropertyContext>>(SET)
    object MapCount: StorageTypeEnum<IsMapDefinition<Any, Any, IsPropertyContext>>(MAP)
    object TypeValue: StorageTypeEnum<IsMultiTypeDefinition<IndexedEnum<Any>, IsPropertyContext>>(VALUE)

    @Suppress("UNCHECKED_CAST")
    fun castDefinition(definition: IsPropertyDefinition<*>) = definition as T
}

typealias ValueProcessor<T> = (StorageTypeEnum<T>, ByteArray, T, Any) -> Unit
private typealias QualifierWriter = ((Byte) -> Unit) -> Unit

/**
 * Walk Values and process storable values.
 * Pass [valueProcessor] to process values
 */
fun <DM: IsDataModel<P>, P: AbstractPropertyDefinitions<*>> AbstractValues<*, DM, P>.walkForStorage(
    valueProcessor: ValueProcessor<IsPropertyDefinition<*>>
) = this.walkForStorage(0, null, valueProcessor)

/**
 * Walk Values and process storable values.
 * [qualifierCount], [qualifierWriter] define the count and writer for any parent property
 * Pass [valueProcessor] to process values
 */
private fun <DM: IsDataModel<P>, P: AbstractPropertyDefinitions<*>> AbstractValues<*, DM, P>.walkForStorage(
    qualifierCount: Int = 0,
    qualifierWriter: QualifierWriter? = null,
    valueProcessor: ValueProcessor<IsPropertyDefinition<*>>
) {
    for ((index, value) in this.values) {
        val definition = this.dataModel.properties[index]!!
        processValue(definition.index, definition.definition, value, valueProcessor, qualifierCount, qualifierWriter)
    }
}

/**
 * Process a single value with [valueProcessor] at [index] with [definition] and [value]
 * [qualifierLength], [qualifierWriter] define the count and writer for any parent property
 * If index is -1, this value has no index.
 */
@Suppress("UNCHECKED_CAST")
private fun <T: IsPropertyDefinition<*>> processValue(
    index: Int,
    definition: T,
    value: Any,
    valueProcessor: ValueProcessor<T>,
    qualifierLength: Int,
    qualifierWriter: QualifierWriter? = null
) {
    when (value) {
        is List<*> -> {
            val listQualifierWriter = createQualifierWriter(qualifierWriter, index, LIST)
            val listQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process List Count
            valueProcessor(ListCount as StorageTypeEnum<T>, writeQualifier(listQualifierCount, listQualifierWriter), definition, value.size) // for list count

            // Process List values
            val listValueDefinition = (definition as ListDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
            for ((listIndex, listItem) in (value as List<Any>).withIndex()) {
                val listValueQualifierWriter: QualifierWriter = { writer ->
                    listQualifierWriter.invoke(writer)
                    listIndex.toUInt().writeBytes(writer, 4)
                }
                processValue(
                    -1, listValueDefinition, listItem,
                    valueProcessor as ValueProcessor<IsValueDefinition<*, *>>,
                    listQualifierCount + 4,
                    listValueQualifierWriter
                )
            }
        }
        is Set<*> -> {
            val setQualifierWriter = createQualifierWriter(qualifierWriter, index, SET)
            val setQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process Set Count
            valueProcessor(SetCount as StorageTypeEnum<T>, writeQualifier(setQualifierCount, setQualifierWriter), definition, value.size) // for set count

            // Process Set Values
            val setValueDefinition = (definition as SetDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
            for (setItem in (value as Set<Any>)) {
                val setValueQualifierWriter: QualifierWriter = { writer ->
                    setQualifierWriter.invoke(writer)
                    setValueDefinition.writeStorageBytes(setItem, writer)
                }
                processValue(
                    -1, setValueDefinition, setItem,
                    valueProcessor as ValueProcessor<IsValueDefinition<*, *>>,
                    setQualifierCount + setValueDefinition.calculateStorageByteLength(setItem),
                    setValueQualifierWriter
                )
            }
        }
        is Map<*, *> -> {
            val mapQualifierWriter = createQualifierWriter(qualifierWriter, index, MAP)
            val mapQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            // Process Map Count
            valueProcessor(MapCount as StorageTypeEnum<T>, writeQualifier(mapQualifierCount, mapQualifierWriter), definition, value.size) // for map count

            // Process Map Values
            val mapDefinition = (definition as IsMapDefinition<Any, *, *>)
            for ((key, mapValue) in (value as Map<Any, Any>)) {
                val mapValueQualifierWriter: QualifierWriter = { writer ->
                    mapQualifierWriter.invoke(writer)
                    mapDefinition.keyDefinition.writeStorageBytes(key, writer)
                }
                processValue(
                    -1, mapDefinition.valueDefinition, mapValue,
                    valueProcessor as ValueProcessor<IsSubDefinition<*, *>>,
                    mapQualifierCount + mapDefinition.keyDefinition.calculateStorageByteLength(key),
                    mapValueQualifierWriter
                )
            }
        }
        is AbstractValues<*, *, *> -> {
            val indexWriter = createQualifierWriter(qualifierWriter, index, VALUE)
            val abstractValuesQualifierCount = qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            (value as AnyAbstractValues).walkForStorage(abstractValuesQualifierCount, indexWriter, valueProcessor as ValueProcessor<IsPropertyDefinition<*>>)
        }
        is TypedValue<*, *> -> {
            val qualifier = writeQualifier(
                qualifierLength + index.calculateVarIntWithExtraInfoByteSize(),
                createQualifierWriter(qualifierWriter, index, VALUE)
            )
            valueProcessor(TypeValue as StorageTypeEnum<T>, qualifier, definition, value)
        }
        else -> {
            val qualifier = if (index > -1) {
                writeQualifier(
                    qualifierLength + index.calculateVarIntWithExtraInfoByteSize(),
                    createQualifierWriter(qualifierWriter, index, VALUE)
                )
            } else writeQualifier(qualifierLength, qualifierWriter)

            valueProcessor(Value as StorageTypeEnum<T>, qualifier, definition, value)
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