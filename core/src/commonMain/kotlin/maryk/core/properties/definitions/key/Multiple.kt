package maryk.core.properties.definitions.key

import maryk.core.models.SingleValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values

/** Class to encode multiple [references] for key or other indexable usages */
data class Multiple(
    val references: List<IsFixedBytesPropertyReference<*>>
): IsIndexable {
    override val indexKeyPartType = IndexKeyPartType.Multiple
    override val byteSize = this.calculateSize(references)
    val indices: IntArray

    init {
        var index = 0
        // Add indices to array. Also account for the 1 sized separator
        indices = references.map { def -> index.also { index += def.byteSize + 1 } }.toIntArray()
    }

    /** Convenience method to set with each [reference] as separate argument */
    constructor(vararg reference: IsFixedBytesPropertyReference<*>): this(listOf(*reference))

    /**
     * Write bytes for storage of multi indexable for [values] to [writer]
     * Returns true if it is a complete indexable. False if not and should not be written.
     */
    fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit): Boolean {
        for ((keyIndex, reference) in this.references.withIndex()) {
            @Suppress("UNCHECKED_CAST")
            val value = (reference as IsFixedBytesPropertyReference<Any>).getValue(values)

            reference.propertyDefinition.writeStorageBytes(value, writer)

            // Add separator
            if (keyIndex < this.references.lastIndex) {
                writer(1)
            }
        }

        return true
    }

    private fun calculateSize(keyDefinitions: List<IsFixedBytesPropertyReference<*>>): Int {
        var totalBytes = keyDefinitions.size - 1 // Start with adding size of separators
        for (it in keyDefinitions) {
            totalBytes += it.propertyDefinition.byteSize
        }
        return totalBytes
    }

    object Properties : ObjectPropertyDefinitions<Multiple>() {
        @Suppress("UNCHECKED_CAST")
        val references = add(1, "references",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfSimpleIndexKeyPartDefinitions
                )
            ) as ListDefinition<TypedValue<IndexKeyPartType, IsFixedBytesPropertyReference<*>>, IsPropertyContext>,
            toSerializable = { value ->
                TypedValue(value.indexKeyPartType, value)
            },
            fromSerializable = { typedValue ->
                typedValue.value
            },
            getter = Multiple::references
        )
    }

    internal object Model : SingleValueDataModel<List<TypedValue<IndexKeyPartType, IsFixedBytesPropertyReference<*>>>, List<IsFixedBytesPropertyReference<*>>, Multiple, Properties, DefinitionsConversionContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.references
    ) {
        override fun invoke(values: ObjectValues<Multiple, Properties>) = Multiple(
            references = values(1)
        )
    }
}
