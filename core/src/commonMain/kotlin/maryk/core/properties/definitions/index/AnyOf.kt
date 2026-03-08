package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Class to fan out multiple string references as separate index entries. Optional [name] exposes it as a search index. */
data class AnyOf(
    val name: String? = null,
    val references: List<IsIndexablePropertyReference<String>>
) : IsIndexable {
    override val indexKeyPartType = IndexKeyPartType.AnyOf
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    constructor(vararg reference: IsIndexablePropertyReference<String>) : this(null, listOf(*reference))
    constructor(name: String, vararg reference: IsIndexablePropertyReference<String>) : this(name, listOf(*reference))

    override fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> =
        references.flatMap { it.toStorageByteArrays(values) }.distinct()

    override fun calculateReferenceStorageByteLength() =
        this.indexKeyPartType.index.calculateVarByteLength() + references.sumOf {
            it.calculateReferenceStorageByteLength().let { length ->
                length + length.calculateVarByteLength()
            }
        }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.indexKeyPartType.index.writeVarBytes(writer)
        for (reference in references) {
            reference.calculateReferenceStorageByteLength().writeVarBytes(writer)
            reference.writeReferenceStorageBytes(writer)
        }
    }

    override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?) =
        toStorageByteArrayForIndex(values, ByteArray(keySize ?: 0))?.size ?: 0

    override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (byte: Byte) -> Unit) {
        toStorageByteArrayForIndex(values, key)?.forEach(writer)
    }

    override fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit) {
        references.firstOrNull()?.writeStorageBytes(values, writer)
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        references.all { it.isCompatibleWithModel(dataModel) }

    internal object Model :
        ContextualDataModel<AnyOf, Model, DefinitionsConversionContext, AnyOfContext>(
            contextTransformer = { AnyOfContext(it) }
        ) {
        val name by string(
            index = 1u,
            required = false,
            final = true,
            getter = AnyOf::name
        )

        @Suppress("UNCHECKED_CAST")
        val references by list(
            index = 2u,
            getter = AnyOf::references,
            valueDefinition = ContextTransformerDefinition(
                contextTransformer = { context: AnyOfContext? -> context?.definitionsConversionContext },
                definition = InternalMultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfStringIndexKeyPartDefinitions,
                    keepAsValues = true
                )
            ),
            toSerializable = { value ->
                TypedValue(value.indexKeyPartType, value)
            },
            fromSerializable = { it.toStringIndexablePropertyReference() }
        )

        override fun invoke(values: ObjectValues<AnyOf, Model>) = AnyOf(
            name = values(1u),
            references = values(2u)
        )
    }
}

class AnyOfContext(
    val definitionsConversionContext: DefinitionsConversionContext?
) : IsPropertyContext
