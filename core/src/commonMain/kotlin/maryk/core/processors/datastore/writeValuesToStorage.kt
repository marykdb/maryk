package maryk.core.processors.datastore

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.models.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.ReferenceType
import maryk.core.properties.references.ReferenceType.DELETE
import maryk.core.properties.references.ReferenceType.EMBED
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.types.TypedValue
import maryk.core.values.AbstractValues
import maryk.core.values.AnyAbstractValues

sealed class StorageTypeEnum<out T : IsPropertyDefinition<*>>(val referenceType: ReferenceType) {
    object ObjectDelete : StorageTypeEnum<IsPropertyDefinition<Boolean>>(DELETE)
    object Value : StorageTypeEnum<IsSimpleValueDefinition<Any, IsPropertyContext>>(VALUE)
    object ListSize : StorageTypeEnum<IsListDefinition<Any, IsPropertyContext>>(LIST)
    object SetSize : StorageTypeEnum<IsSetDefinition<Any, IsPropertyContext>>(SET)
    object MapSize : StorageTypeEnum<IsMapDefinition<Any, Any, IsPropertyContext>>(MAP)
    object TypeValue : StorageTypeEnum<IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>>(TYPE)
    object Embed : StorageTypeEnum<IsEmbeddedValuesDefinition<*, *>>(EMBED)

    @Suppress("UNCHECKED_CAST")
    fun castDefinition(definition: IsPropertyDefinition<*>?) = definition as T
}

typealias ValueWriter<T> = (StorageTypeEnum<T>, ByteArray, T, Any) -> Unit
internal typealias QualifierWriter = ((Byte) -> Unit) -> Unit

/**
 * Walk Values and process storable values.
 * Pass [valueWriter] to process values
 */
fun <DM : AbstractDataModel<*>> AbstractValues<*, DM>.writeToStorage(
    valueWriter: ValueWriter<IsPropertyDefinition<*>>
) = this.writeToStorage(0, null, valueWriter)

/**
 * Walk Values and process storable values.
 * [qualifierCount], [qualifierWriter] define the count and writer for any parent property
 * Pass [valueWriter] to process values
 */
fun <DM : IsTypedDataModel<*>> AbstractValues<*, DM>.writeToStorage(
    qualifierCount: Int = 0,
    qualifierWriter: QualifierWriter? = null,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>
) {
    for ((index, value) in this.values) {
        val definition = this.dataModel[index]!!
        writeValue(definition.index, qualifierCount, qualifierWriter, definition.definition, value, valueWriter)
    }
}

/**
 * Process a single value with [valueWriter] at [index] with [definition] and [value]
 * [qualifierLength], [qualifierWriter] define the count and writer for any parent property
 * If index is -1, this value has no index.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>> writeValue(
    index: UInt?,
    qualifierLength: Int,
    qualifierWriter: QualifierWriter? = null,
    definition: T,
    value: Any,
    valueWriter: ValueWriter<T>
) {
    when (value) {
        is List<*> -> {
            if (definition !is IsListDefinition<*, *>) {
                throw TypeException("Definition should be a ListDefinition for a List")
            }

            val newIndex = index ?: 0u

            val listQualifierWriter = createQualifierWriter(qualifierWriter, newIndex, LIST)
            val listQualifierCount = qualifierLength + newIndex.calculateVarIntWithExtraInfoByteSize()
            writeListToStorage(
                listQualifierCount,
                listQualifierWriter,
                valueWriter,
                definition,
                value
            )
        }
        is Set<*> -> {
            val newIndex = index ?: 0u

            val setQualifierWriter = createQualifierWriter(qualifierWriter, newIndex, SET)
            val setQualifierCount = qualifierLength + newIndex.calculateVarIntWithExtraInfoByteSize()
            writeSetToStorage(
                setQualifierCount,
                setQualifierWriter,
                valueWriter,
                definition,
                value
            )
        }
        is Map<*, *> -> {
            if (definition !is IsMapDefinition<*, *, *>) {
                throw TypeException("Definition should be a MapDefinition for a Map")
            }

            val newIndex = index ?: 0u

            val mapQualifierWriter = createQualifierWriter(
                qualifierWriter,
                newIndex,
                MAP
            )
            val mapQualifierCount = qualifierLength + newIndex.calculateVarIntWithExtraInfoByteSize()
            writeMapToStorage(
                mapQualifierCount,
                mapQualifierWriter,
                valueWriter as ValueWriter<IsMapDefinition<*, *, *>>,
                definition as IsMapDefinition<*, *, *>,
                value as Map<Any, Any>
            )
        }
        is AbstractValues<*, *> -> {
            if (definition !is EmbeddedValuesDefinition<*>) {
                throw TypeException("Expected Embedded Values Definition for Values object")
            }

            val indexWriter =
                if (index == null) qualifierWriter else createQualifierWriter(qualifierWriter, index, EMBED)
            val abstractValuesQualifierCount =
                if (index == null) qualifierLength else qualifierLength + index.calculateVarIntWithExtraInfoByteSize()

            // Write complex values existence indicator
            // Write parent value with Unit, so it knows this one is not deleted. So possible lingering old types are not read.
            val qualifier = writeQualifier(abstractValuesQualifierCount, indexWriter)
            valueWriter(Embed as StorageTypeEnum<T>, qualifier, definition, Unit)

            (value as AnyAbstractValues).writeToStorage(
                abstractValuesQualifierCount,
                indexWriter,
                valueWriter as ValueWriter<IsPropertyDefinition<*>>
            )
        }
        is TypedValue<*, *> -> {
            if (definition !is IsMultiTypeDefinition<*, *, *>) {
                throw TypeException("Definition should be a MultiTypeDefinition for a TypedValue")
            }

            val valueQualifierWriter = if (index != null) {
                createQualifierWriter(qualifierWriter, index, VALUE)
            } else qualifierWriter
            val valueQualifierSize = if (index != null) {
                qualifierLength + index.calculateVarIntWithExtraInfoByteSize()
            } else qualifierLength

            writeTypedValueToStorage(
                valueQualifierSize,
                valueQualifierWriter,
                valueWriter,
                definition,
                value
            )
        }
        else -> {
            val qualifier = if (index != null) {
                writeQualifier(
                    qualifierLength + index.calculateVarIntWithExtraInfoByteSize(),
                    createQualifierWriter(qualifierWriter, index, VALUE)
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
internal fun createQualifierWriter(
    parentQualifierWriter: QualifierWriter?,
    index: UInt,
    referenceType: ReferenceType
): QualifierWriter = { writer ->
    parentQualifierWriter?.invoke(writer)
    index.writeVarIntWithExtraInfo(referenceType.value, writer)
}

/**
 * Write a specific qualifier with passed [qualifierLength] and [qualifierWriter]
 */
internal fun writeQualifier(
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
