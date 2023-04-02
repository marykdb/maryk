package maryk.generator.kotlin

import maryk.core.models.IsBaseObjectDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.contextual.ContextualMapDefinition

/** Describes the property definitions for translation to kotlin */
internal open class PropertyDefinitionKotlinDescriptor<in T : Any, D : IsTransportablePropertyDefinitionType<in T>, DM : IsObjectDataModel<D>>(
    val className: String,
    val wrapFunctionName: String,
    val kotlinTypeName: (D) -> String,
    val definitionModel: IsBaseObjectDataModel<D, DM, *, *>,
    val propertyValueOverride: Map<String, (IsTransportablePropertyDefinitionType<out Any>, Any, (String) -> Unit) -> String?> = mapOf(),
    val propertyNameOverride: Map<String, String> = mapOf(),
    private val imports: ((D) -> Array<String>?)? = null
) {
    /** Get an array of all imports which are always needed for this property [definition] */
    fun getImports(definition: D): Array<String> {
        this.imports?.invoke(definition)?.let {
            return it
        }

        return Array(0) { "" }
    }

    /**
     * Create kotlin code to define given property [definition]
     * [addImport] is called if any imports need to be added
     */
    fun definitionToKotlin(definition: D, addImport: (String) -> Unit): String {
        val fields = this.definitionToKotlinFields(definition, addImport)

        return if (fields.isBlank()) {
            "\n$className()"
        } else {
            "\n$className(\n${fields.prependIndent()}\n)"
        }
    }

    /**
     * Create kotlin code to define given property [definition]
     * [addImport] is called if any imports need to be added
     */
    fun definitionToKotlinFields(definition: D, addImport: (String) -> Unit): String {
        val output = mutableListOf<String>()

        properties@ for (property in definitionModel) {
            val value = property.getter(definition)

            val def = property.definition
            if (value != null && (def !is HasDefaultValueDefinition<*> || value != def.default)) {
                val override = this.propertyValueOverride[property.name]
                val propertyName = this.propertyNameOverride[property.name] ?: property.name

                if (override != null) {
                    override(definition as IsTransportablePropertyDefinitionType<*>, value, addImport)?.let {
                        output.add("""$propertyName = $it""")
                    }
                } else {
                    val defToSend = if (def is ContextualMapDefinition<*, *, *>) {
                        @Suppress("UNCHECKED_CAST")
                        definition as IsPropertyDefinition<in Any>
                    } else {
                        def
                    }
                    output.add("""${property.name} = ${generateKotlinValue(defToSend, value, addImport, addGenerics = true)}""")
                }
            }
        }

        return if (output.isEmpty()) "" else output.joinToString(",\n")
    }
}
