package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.json.JsonGenerator
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.TypedValue

/**
 * Definition for objects with multiple types
 * @param typeMap definition of all sub types
 */
class MultiTypeDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val typeMap: Map<Int, AbstractSubDefinition<*>>
) : AbstractPropertyDefinition<TypedValue<*>>(
        name, index, indexed, searchable, required, final
) {
    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: TypedValue<*>?, newValue: TypedValue<*>?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.typeMap[newValue.typeIndex] as AbstractSubDefinition<Any>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.typeIndex} for ${this.getRef(parentRefFactory).completeName}")

            definition.validate(
                    previousValue?.value,
                    newValue.value
            ) {
                getRef(parentRefFactory)
            }
        }
    }

    override fun writeJsonValue(generator: JsonGenerator, value: TypedValue<Any>) {
        generator.writeStartArray()
        generator.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.typeMap[value.typeIndex] as AbstractSubDefinition<Any>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex} for $name")

        definition.writeJsonValue(generator, value.value)
        generator.writeEndArray()
    }
}