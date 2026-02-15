package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.BaseDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsValuePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/**
 * Index key part which fills missing values with the maximum
 * allowed value for the referenced property. Useful for indexing
 * periods with an open end date.
 */
data class ReferenceToMax<T : Any>(
    val reference: IsValuePropertyReference<T, *, *, *>
) : IsIndexablePropertyReference<T> {
    override val indexKeyPartType = IndexKeyPartType.ReferenceToMax
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    @Suppress("UNCHECKED_CAST")
    private fun maxValue(): T = when (val def = reference.comparablePropertyDefinition as Any) {
        is NumberDefinition<*> -> (def.maxValue ?: when(def.type) {
            SInt64 -> Long.MAX_VALUE
            SInt32 -> Int.MAX_VALUE
            SInt16 -> Short.MAX_VALUE
            SInt8 -> Byte.MAX_VALUE
            UInt64 -> ULong.MAX_VALUE
            UInt32 -> UInt.MAX_VALUE
            UInt16 -> UShort.MAX_VALUE
            UInt8 -> UByte.MAX_VALUE
            Float64 -> Double.MAX_VALUE
            Float32 -> Float.MAX_VALUE
            else -> throw IllegalStateException("Unknown number type ${def.type}")
        }) as T
        is DateDefinition -> (def.maxValue ?: DateDefinition.MAX) as T
        is DateTimeDefinition -> (def.maxValue ?: when (def.precision) {
            TimePrecision.SECONDS -> DateTimeDefinition.MAX_IN_SECONDS
            TimePrecision.MILLIS -> DateTimeDefinition.MAX_IN_MILLIS
            TimePrecision.NANOS -> DateTimeDefinition.MAX_IN_NANOS
        }) as T
        is TimeDefinition -> (def.maxValue ?: when (def.precision) {
            TimePrecision.SECONDS -> TimeDefinition.MAX_IN_SECONDS
            TimePrecision.MILLIS -> TimeDefinition.MAX_IN_MILLIS
            TimePrecision.NANOS -> TimeDefinition.MAX_IN_NANOS
        }) as T
        is FixedBytesDefinition -> (def.maxValue ?: Bytes(ByteArray(def.byteSize) { 0xFF.toByte() })) as T
        else -> throw RequiredException(reference)
    }

    override fun getValue(values: IsValuesGetter): T = try {
        reference.getValue(values)
    } catch (_: RequiredException) {
        maxValue()
    }

    override fun calculateStorageByteLength(value: T) = reference.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) =
        reference.writeStorageBytes(value, writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): T =
        reference.readStorageBytes(length, reader)

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        reference == propertyReference

    override fun calculateReferenceStorageByteLength(): Int {
        val refLength = reference.calculateStorageByteLength()
        return refLength.calculateVarIntWithExtraInfoByteSize() + refLength
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        val refLength = reference.calculateStorageByteLength()
        refLength.writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        reference.writeStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    override fun toQualifierStorageByteArray() = reference.toQualifierStorageByteArray()

    internal object Model : SingleTypedValueDataModel<AnyPropertyReference, ReferenceToMax<out Any>, Model, DefinitionsConversionContext>(
        singlePropertyDefinitionGetter = { Model.reference }
    ) {
        val reference by contextual(
            index = 1u,
            getter = ReferenceToMax<*>::reference,
            definition = ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                contextualResolver = {
                    it?.propertyDefinitions as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<ReferenceToMax<out Any>, Model>) = ReferenceToMax<Any>(
            reference = values(1u)
        )
    }
}
