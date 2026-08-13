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
import maryk.core.values.ObjectValues

/** Reads the index signature emitted by Normalize before deterministic NFD. */
internal class LegacyNormalize(
    val reference: IsIndexablePropertyReference<String>,
) : IsIndexablePropertyReference<String> by reference {
    override val indexKeyPartType = IndexKeyPartType.Normalize
    override val referenceStorageByteArray by lazy { Bytes(toReferenceStorageByteArray()) }

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        reference.isForPropertyReference(propertyReference)

    override fun calculateReferenceStorageByteLength() =
        reference.calculateReferenceStorageByteLength().let { referenceLength ->
            referenceLength.calculateVarIntWithExtraInfoByteSize() + referenceLength
        }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        reference.calculateReferenceStorageByteLength().writeVarIntWithExtraInfo(
            indexKeyPartType.index.toByte(),
            writer,
        )
        reference.writeReferenceStorageBytes(writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    override fun toQualifierStorageByteArray() = reference.toQualifierStorageByteArray()

    internal object Model :
        SingleValueDataModel<TypedValue<IndexKeyPartType<*>, IsIndexable>, IsIndexable, LegacyNormalize, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.reference },
        ) {
        val reference by contextual(
            index = 1u,
            getter = LegacyNormalize::reference,
            definition = ContextTransformerDefinition(
                contextTransformer = { context: DefinitionsConversionContext? -> context },
                definition = InternalMultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfStringIndexKeyPartDefinitions,
                    keepAsValues = true,
                ),
            ),
            toSerializable = { value, _ -> value?.let { TypedValue(it.indexKeyPartType, it) } },
            fromSerializable = { it?.toStringIndexablePropertyReference() },
        )

        override fun invoke(values: ObjectValues<LegacyNormalize, Model>) = LegacyNormalize(
            reference = values(1u)!!,
        )
    }
}
