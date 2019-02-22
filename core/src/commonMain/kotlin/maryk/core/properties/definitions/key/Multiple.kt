package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.SingleValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values

/** Class to encode multiple [references] for key or other indexable usages */
data class Multiple(
    val references: List<IsIndexablePropertyReference<*>>
): IsIndexable {
    override val indexKeyPartType = IndexKeyPartType.Multiple

    /** Convenience method to set with each [reference] as separate argument */
    constructor(vararg reference: IsIndexablePropertyReference<*>): this(listOf(*reference))

    override fun calculateStorageByteLength(values: Values<*, *>): Int {
        var totalBytes = references.size - 1 // Start with adding size of separators
        for (it in references) {
            totalBytes += it.calculateStorageByteLength(values)
        }
        return totalBytes
    }

    override fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit) {
        for ((keyIndex, reference) in this.references.withIndex()) {
            @Suppress("UNCHECKED_CAST")
            val value = (reference as IsIndexablePropertyReference<Any>).getValue(values)

            reference.writeStorageBytes(value, writer)

            // Add separator
            if (keyIndex < this.references.lastIndex) {
                writer(1)
            }
        }
    }

    /**
     * Add all lengths of sub references + their lengths
     */
    override fun calculateReferenceStorageByteLength() =
        this.indexKeyPartType.index.calculateVarByteLength() + references.sumBy {
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

    object Properties : ObjectPropertyDefinitions<Multiple>() {
        @Suppress("UNCHECKED_CAST")
        val references = add(1, "references",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfSimpleIndexKeyPartDefinitions
                )
            ) as ListDefinition<TypedValue<IndexKeyPartType, IsIndexablePropertyReference<*>>, IsPropertyContext>,
            toSerializable = { value ->
                TypedValue(value.indexKeyPartType, value)
            },
            fromSerializable = { typedValue ->
                typedValue.value
            },
            getter = Multiple::references
        )
    }

    internal object Model : SingleValueDataModel<List<TypedValue<IndexKeyPartType, IsIndexablePropertyReference<*>>>, List<IsIndexablePropertyReference<*>>, Multiple, Properties, DefinitionsConversionContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.references
    ) {
        override fun invoke(values: ObjectValues<Multiple, Properties>) = Multiple(
            references = values(1)
        )
    }
}
