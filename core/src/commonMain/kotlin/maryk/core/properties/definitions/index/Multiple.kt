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
    override val indexPartCount by lazy { references.sumOf { it.indexPartCount } }

    /** Convenience method to set with each [reference] as separate argument */
    constructor(vararg reference: IsIndexable) : this(listOf(*reference))

    override fun toStorageByteArraysForIndex(values: IsValuesGetter, key: ByteArray?): List<ByteArray> =
        buildList {
            forEachStorageByteArrayForIndex(values, key, ::add)
        }

    override fun forEachStorageByteArrayForIndex(
        values: IsValuesGetter,
        key: ByteArray?,
        emit: (ByteArray) -> Unit
    ) {
        val selectedValues = arrayOfNulls<ByteArray>(references.size)
        val partLengths = IntArray(references.size)
        val keySize = key?.size ?: 0

        fun appendCombination(depth: Int, valueBytesLength: Int, lengthBytesLength: Int) {
            if (depth == references.size) {
                val totalLength = valueBytesLength
                    .checkedIndexByteLengthPlus(lengthBytesLength)
                    .checkedIndexByteLengthPlus(keySize)

                emit(ByteArray(totalLength).also { output ->
                    var writeIndex = 0
                    selectedValues.forEach { selectedValue ->
                        val bytes = selectedValue ?: return@forEach
                        bytes.copyInto(output, writeIndex)
                        writeIndex += bytes.size
                    }

                    for (sizeIndex in partLengths.lastIndex downTo 0) {
                        partLengths[sizeIndex].writeVarBytes { output[writeIndex++] = it }
                    }

                    key?.copyInto(output, writeIndex)
                })
                return
            }

            references[depth].forEachStorageByteArray(values) { option ->
                selectedValues[depth] = option
                partLengths[depth] = option.size
                appendCombination(
                    depth = depth + 1,
                    valueBytesLength = valueBytesLength.checkedIndexByteLengthPlus(option.size),
                    lengthBytesLength = lengthBytesLength.checkedIndexByteLengthPlus(option.size.calculateVarByteLength())
                )
            }
        }

        appendCombination(depth = 0, valueBytesLength = 0, lengthBytesLength = 0)
    }

    override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?): Int {
        val subValues = references.map { it.toStorageByteArrays(values) }
        if (subValues.any { it.isEmpty() }) {
            return 0
        }

        val longestCombination = subValues.sumOf { options ->
            val maxLength = options.maxOf { it.size }
            maxLength.checkedIndexByteLengthPlus(maxLength.calculateVarByteLength())
        }

        return longestCombination.checkedIndexByteLengthPlus(keySize ?: 0)
    }

    override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (byte: Byte) -> Unit) {
        var written = false
        forEachStorageByteArrayForIndex(values, key) { bytes ->
            if (!written) {
                bytes.forEach(writer)
                written = true
            }
        }
    }

    override fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit) {
        for (reference in references) {
            var found = false
            reference.forEachStorageByteArray(values) { bytes ->
                if (!found) {
                    bytes.forEach(writer)
                    found = true
                }
            }
            if (!found) return
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
}

internal fun Int.checkedIndexByteLengthPlus(addend: Int): Int {
    require(addend >= 0) { "Index byte length cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Index byte length exceeds Int range" }
    return this + addend
}
