package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleValueDataModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Class to split string values by [on] for [reference] in index. */
data class Split(
    val reference: IsIndexablePropertyReference<String>,
    val on: SplitOn
) : IsIndexablePropertyReference<String> {
    override val indexKeyPartType = IndexKeyPartType.Split
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override fun getValue(values: IsValuesGetter) =
        splitValues(referenceValues(values)).first().decodeToString()

    override fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> =
        splitValues(referenceValues(values)).distinct()

    private fun referenceValues(values: IsValuesGetter) = when (reference) {
        is Normalize -> reference.reference.toStorageByteArrays(values)
        else -> reference.toStorageByteArrays(values)
    }

    private fun splitValues(values: List<ByteArray>): List<ByteArray> =
        values.flatMap { bytes ->
            reference.splitTokens(bytes, on)
        }.map { splitValue ->
            ByteArray(reference.calculateStorageByteLength(splitValue)).also { byteArray ->
                var writeIndex = 0
                reference.writeStorageBytes(splitValue) { byteArray[writeIndex++] = it }
            }
        }

    override fun calculateStorageByteLength(value: String) =
        reference.calculateStorageByteLength(reference.splitQueryTokens(value, on).firstOrNull() ?: "")

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) =
        reference.writeStorageBytes(reference.splitQueryTokens(value, on).firstOrNull() ?: "", writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): String =
        reference.readStorageBytes(length, reader)

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        reference.isForPropertyReference(propertyReference)

    override fun calculateReferenceStorageByteLength() =
        reference.calculateReferenceStorageByteLength().let { refLength ->
            refLength.calculateVarIntWithExtraInfoByteSize() + refLength + 1
        }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        reference.calculateReferenceStorageByteLength().writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        reference.writeReferenceStorageBytes(writer)
        writer(on.index.toByte())
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    override fun toQualifierStorageByteArray() = reference.toQualifierStorageByteArray()

    internal object Model :
        SingleValueDataModel<TypedValue<IndexKeyPartType<*>, IsIndexable>, IsIndexable, Split, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.reference }
        ) {
        @Suppress("UNCHECKED_CAST")
        val reference by contextual(
            index = 1u,
            getter = Split::reference,
            definition = ContextTransformerDefinition(
                contextTransformer = { context: DefinitionsConversionContext? -> context },
                definition = InternalMultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfStringIndexKeyPartDefinitions,
                    keepAsValues = true
                )
            ),
            toSerializable = { value, _ -> value?.let { TypedValue(it.indexKeyPartType, it) } },
            fromSerializable = { it?.value as IsIndexablePropertyReference<String>? }
        )

        val on by enum(
            index = 2u,
            getter = Split::on,
            enum = SplitOn
        )

        override fun invoke(values: ObjectValues<Split, Model>) = Split(
            reference = values(1u)!!,
            on = values(2u)
        )
    }
}

private fun IsIndexablePropertyReference<String>.splitTokens(bytes: ByteArray, on: SplitOn): List<String> = when (this) {
    is Normalize -> this.reference.splitTokens(bytes, on).map(::normalizeStringForIndex)
    else -> {
        var readIndex = 0
        splitStringForIndex(this.readStorageBytes(bytes.size) { bytes[readIndex++] }, on)
    }
}

private fun IsIndexablePropertyReference<String>.splitQueryTokens(value: String, on: SplitOn): List<String> = when (this) {
    is Normalize -> this.reference.splitQueryTokens(value, on).map(::normalizeStringForIndex)
    else -> splitStringForIndex(value, on)
}
