package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.BaseDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsValuePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Class to normalize string values by [reference] in index. */
data class Normalize(
    val reference: IsValuePropertyReference<String, *, *, *>
) : IsIndexablePropertyReference<String> {
    override val indexKeyPartType = IndexKeyPartType.Normalize
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override fun getValue(values: IsValuesGetter) =
        normalizeStringForIndex(this.reference.getValue(values))

    override fun calculateStorageByteLength(value: String) =
        this.reference.calculateStorageByteLength(normalizeStringForIndex(value))

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) =
        this.reference.writeStorageBytes(normalizeStringForIndex(value), writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte): String =
        normalizeStringForIndex(this.reference.readStorageBytes(length, reader))

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        this.reference == propertyReference

    override fun calculateReferenceStorageByteLength() =
        this.reference.calculateReferenceStorageByteLength()

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.reference.calculateStorageByteLength().writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        this.reference.writeStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    override fun toQualifierStorageByteArray() = this.reference.toStorageByteArray()

    internal object Model :
        SingleTypedValueDataModel<AnyPropertyReference, Normalize, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.reference }
        ) {
        val reference by contextual(
            index = 1u,
            getter = Normalize::reference,
            definition = ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                contextualResolver = {
                    it?.propertyDefinitions as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<Normalize, Model>) = Normalize(
            reference = values(1u)
        )
    }
}
