package maryk.generator.kotlin

import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.enum.IndexedEnum

@Suppress("UNCHECKED_CAST")
internal fun <DO: Any> AbstractPropertyDefinitions<DO>.generateKotlin(
    addImport: (String) -> Unit,
    generationContext: GenerationContext? = null,
    addEnumDefinition: ((String) -> Unit)? = null
): List<KotlinForProperty> {
    val propertiesKotlin = mutableListOf<KotlinForProperty>()
    for (propertyDefinitionWrapper in this) {
        val definition = propertyDefinitionWrapper.definition as? IsTransportablePropertyDefinitionType<Any>
                ?: throw Exception("Property definition is not supported: ${propertyDefinitionWrapper.definition}")

        val kotlinDescriptor =
            definition.getKotlinDescriptor()

        if (definition.propertyDefinitionType == PropertyDefinitionType.Enum) {
            (definition as EnumDefinition<*>).enum.let { enum ->
                if (generationContext?.enums?.contains(enum) != true) {
                    @Suppress("UNCHECKED_CAST")
                    val enumDefinition = (definition as EnumDefinition<IndexedEnum<Any>>).enum

                    addEnumDefinition?.invoke(
                        enumDefinition.generateKotlinClass(addImport)
                    )
                }
            }
        }

        val default = if(definition is HasDefaultValueDefinition<*> && definition.default != null) {
            " = ${generateKotlinValue(definition, definition.default as Any, addImport)}"
        } else {
            ""
        }

        val nativeTypeName = kotlinDescriptor.kotlinTypeName(definition)

        for (import in kotlinDescriptor.getImports(definition)) {
            addImport(import)
        }

        propertiesKotlin.add(
            KotlinForProperty(
                name = propertyDefinitionWrapper.name,
                index = propertyDefinitionWrapper.index,
                value = """${propertyDefinitionWrapper.name}: $nativeTypeName$default""",
                assign = """this.${propertyDefinitionWrapper.name} with ${propertyDefinitionWrapper.name}""",
                definition = kotlinDescriptor.definitionToKotlin(definition, addImport),
                invoke = "map(${propertyDefinitionWrapper.index})"
            )
        )
    }
    return propertiesKotlin
}
