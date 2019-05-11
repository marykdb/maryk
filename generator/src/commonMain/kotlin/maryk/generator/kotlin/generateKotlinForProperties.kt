package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitionType

internal fun AbstractPropertyDefinitions<*>.generateKotlin(
    addImport: (String) -> Unit,
    generationContext: GenerationContext? = null,
    addEnumDefinition: ((String) -> Unit)? = null
): List<KotlinForProperty> {
    val propertiesKotlin = mutableListOf<KotlinForProperty>()
    for (propertyDefinitionWrapper in this) {
        @Suppress("UNCHECKED_CAST")
        val definition = propertyDefinitionWrapper.definition as? IsTransportablePropertyDefinitionType<Any>
            ?: throw TypeException("Property definition is not supported: ${propertyDefinitionWrapper.definition}")

        val kotlinDescriptor =
            definition.getKotlinDescriptor()

        if (definition.propertyDefinitionType == PropertyDefinitionType.Enum) {
            (definition as EnumDefinition<*>).enum.let { enum ->
                if (generationContext?.enums?.contains(enum) != true) {
                    val enumDefinition = definition.enum

                    addEnumDefinition?.invoke(
                        enumDefinition.generateKotlinClass(addImport)
                    )
                }
            }
        }

        val default = if (definition is HasDefaultValueDefinition<*> && definition.default != null) {
            " = ${generateKotlinValue(definition, definition.default as Any, addImport)}"
        } else {
            ""
        }

        val nativeTypeName = ": "+kotlinDescriptor.kotlinTypeName(definition)

        for (import in kotlinDescriptor.getImports(definition)) {
            addImport(import)
        }

        propertiesKotlin.add(
            KotlinForProperty(
                name = propertyDefinitionWrapper.name,
                index = propertyDefinitionWrapper.index,
                altNames = propertyDefinitionWrapper.alternativeNames,
                value = "${propertyDefinitionWrapper.name}$nativeTypeName$default",
                assign = "this.${propertyDefinitionWrapper.name} with ${propertyDefinitionWrapper.name}",
                definition = kotlinDescriptor.definitionToKotlin(definition, addImport),
                invoke = "values(${propertyDefinitionWrapper.index}u)"
            )
        )
    }
    return propertiesKotlin
}
