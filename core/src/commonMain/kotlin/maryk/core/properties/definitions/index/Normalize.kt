package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleValueDataModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Class to normalize string values by [reference] in index. */
data class Normalize(
    val reference: IsIndexablePropertyReference<String>
) : IsIndexablePropertyReference<String> {
    override val indexKeyPartType = IndexKeyPartType.Normalize
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override fun getValue(values: IsValuesGetter) =
        normalizeValues(reference.toStorageByteArrays(values)).first().decodeToString()

    override fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> =
        normalizeValues(reference.toStorageByteArrays(values)).distinct()

    private fun normalizeValues(values: List<ByteArray>): List<ByteArray> =
        values.map { bytes ->
            var readIndex = 0
            normalizeStringForIndex(reference.readStorageBytes(bytes.size) { bytes[readIndex++] })
        }.map { normalizedValue ->
            ByteArray(reference.calculateStorageByteLength(normalizedValue)).also { byteArray ->
                var writeIndex = 0
                reference.writeStorageBytes(normalizedValue) { byteArray[writeIndex++] = it }
            }
        }

    override fun calculateStorageByteLength(value: String) =
        this.reference.calculateStorageByteLength(normalizeStringForIndex(value))

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) =
        this.reference.writeStorageBytes(normalizeStringForIndex(value), writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): String =
        normalizeStringForIndex(this.reference.readStorageBytes(length, reader))

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        this.reference == propertyReference

    override fun calculateReferenceStorageByteLength() =
        this.reference.calculateReferenceStorageByteLength().let { refLength ->
            refLength.calculateVarIntWithExtraInfoByteSize() + refLength
        }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.reference.calculateReferenceStorageByteLength().writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        this.reference.writeReferenceStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    override fun toQualifierStorageByteArray() = this.reference.toQualifierStorageByteArray()

    internal object Model :
        SingleValueDataModel<TypedValue<IndexKeyPartType<*>, IsIndexable>, IsIndexable, Normalize, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.reference }
        ) {
        @Suppress("UNCHECKED_CAST")
        val reference by contextual(
            index = 1u,
            getter = Normalize::reference,
            definition = ContextTransformerDefinition(
                contextTransformer = { context: DefinitionsConversionContext? -> context },
                definition = InternalMultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfStringIndexKeyPartDefinitions,
                    keepAsValues = true
                )
            ),
            toSerializable = { value, _ -> value?.let { TypedValue(it.indexKeyPartType, it) } },
            fromSerializable = { it?.toStringIndexablePropertyReference() }
        )

        override fun invoke(values: ObjectValues<Normalize, Model>) = Normalize(
            reference = values(1u)!!
        )
    }
}
