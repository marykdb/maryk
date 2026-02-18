package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleValueDataModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Class to encode multiple [references] for key or other indexable usages */
data class Multiple(
    val references: List<IsIndexable>
) : IsIndexable {
    override val indexKeyPartType = IndexKeyPartType.Multiple
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    /** Convenience method to set with each [reference] as separate argument */
    constructor(vararg reference: IsIndexable) : this(listOf(*reference))

    override fun toStorageByteArraysForIndex(values: IsValuesGetter, key: ByteArray?): List<ByteArray> {
        val subValues = references.map { it.toStorageByteArrays(values) }
        if (subValues.any { it.isEmpty() }) {
            return emptyList()
        }

        var combinations = listOf(Combination())
        for (options in subValues) {
            val next = mutableListOf<Combination>()
            for (current in combinations) {
                for (option in options) {
                    next += Combination(
                        bytes = current.bytes + option,
                        partLengths = current.partLengths + option.size
                    )
                }
            }
            combinations = next
        }

        return combinations.map { combination ->
            val totalLength = combination.bytes.size +
                combination.partLengths.sumOf { it.calculateVarByteLength() } +
                (key?.size ?: 0)

            ByteArray(totalLength).also { output ->
                var writeIndex = 0
                combination.bytes.copyInto(output, writeIndex)
                writeIndex += combination.bytes.size

                for (sizeIndex in combination.partLengths.lastIndex downTo 0) {
                    combination.partLengths[sizeIndex].writeVarBytes { output[writeIndex++] = it }
                }

                key?.copyInto(output, writeIndex)
            }
        }
    }

    override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?): Int {
        return toStorageByteArrayForIndex(values, ByteArray(keySize ?: 0))?.size ?: 0
    }

    override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (byte: Byte) -> Unit) {
        toStorageByteArrayForIndex(values, key)?.forEach(writer)
    }

    override fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit) {
        for (reference in references) {
            reference.toStorageByteArrays(values).firstOrNull()?.forEach(writer) ?: return
        }
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel): Boolean {
        for (reference in this.references) {
            if (!reference.isCompatibleWithModel(dataModel)) {
                return false
            }
        }
        return true
    }

    /**
     * Add all lengths of sub references + their lengths
     */
    override fun calculateReferenceStorageByteLength() =
        this.indexKeyPartType.index.calculateVarByteLength() + references.sumOf {
            it.calculateReferenceStorageByteLength().let { length ->
                length + length.calculateVarByteLength()
            }
        }

    /**
     * Write defining byte + all sub reference bytes preceded by their length to avoid conflicts
     */
    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.indexKeyPartType.index.writeVarBytes(writer)
        for (reference in this.references) {
            reference.calculateReferenceStorageByteLength().writeVarBytes(writer)
            reference.writeReferenceStorageBytes(writer)
        }
    }

    internal object Model :
        SingleValueDataModel<List<TypedValue<IndexKeyPartType<*>, IsIndexable>>, List<IsIndexable>, Multiple, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.references }
        ) {
        override fun invoke(values: ObjectValues<Multiple, Model>) = Multiple(
            references = values(1u)
        )

        val references by list(
            1u,
            getter = Multiple::references,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = IndexKeyPartType,
                definitionMap = mapOfSimpleIndexKeyPartDefinitions
            ),
            toSerializable = { value ->
                TypedValue(value.indexKeyPartType, value)
            },
            fromSerializable = { typedValue ->
                typedValue.value
            }
        )
    }

    private data class Combination(
        val bytes: ByteArray = ByteArray(0),
        val partLengths: IntArray = IntArray(0)
    )
}
