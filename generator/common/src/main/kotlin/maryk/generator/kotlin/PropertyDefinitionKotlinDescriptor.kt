package maryk.generator.kotlin

import maryk.core.objects.IsDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType

/**
 * Describes the property definitions for translation to kotlin
 */
internal open class PropertyDefinitionKotlinDescriptor<T: Any, D: IsTransportablePropertyDefinitionType<T>>(
    val className: String,
    val kotlinTypeName: (D) -> String,
    val definitionModel: IsDataModel<D>,
    val propertyValueOverride: Map<String, (IsTransportablePropertyDefinitionType<Any>, Any, (String) -> Unit) -> String?> = mapOf(),
    val propertyNameOverride: Map<String, String> = mapOf<String, String>(),
    private val imports: ((D) -> Array<String>?)? = null
) {
    /** Get an array of all imports which are always needed for this property [definition] */
    fun getImports(definition: D): Array<String> {
        val newImports = arrayOf("maryk.core.properties.definitions.$className")
        this.imports?.invoke(definition)?.let {
            return newImports.plus(it)
        }

        return newImports
    }

    /**
     * Create kotlin code to define given property [definition]
     * [addImport] is called if any imports need to be added
     */
    fun definitionToKotlin(definition: D, addImport: (String) -> Unit): String {
        val output = mutableListOf<String>()

        properties@for (property in definitionModel.properties) {
            val value = property.getter(definition)

            val def = property.definition
            if (value != null && (def !is HasDefaultValueDefinition<*> || value != def.default)) {
                val override = this.propertyValueOverride[property.name]
                val propertyName = this.propertyNameOverride[property.name] ?: property.name

                if (override != null) {
                    @Suppress("UNCHECKED_CAST")
                    override(definition as IsTransportablePropertyDefinitionType<Any>, value, addImport)?.let {
                        output.add("""$propertyName = $it""")
                    }
                } else {
                    output.add("""${property.name} = ${generateKotlinValue(def, value, addImport)}""")
                }
            }
        }

        return if (output.isEmpty()) {
            "\n$className()"
        } else {
            "\n$className(\n${output.joinToString(",\n").prependIndent()}\n)"
        }
    }
}
