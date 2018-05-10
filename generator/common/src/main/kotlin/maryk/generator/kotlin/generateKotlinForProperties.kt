package maryk.generator.kotlin

import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsWithDefaultDefinition
import maryk.core.properties.definitions.PropertyDefinitions

@Suppress("UNCHECKED_CAST")
internal fun <DO: Any> PropertyDefinitions<DO>.generateKotlin(addImport: (String) -> Unit): List<KotlinForProperty> {
    val propertiesKotlin = mutableListOf<KotlinForProperty>()
    for (it in this) {
        val definition = it.definition as? IsTransportablePropertyDefinitionType<Any>
                ?: throw Exception("Property definition is not supported: ${it.definition}")

        val names =
            (it.definition as IsTransportablePropertyDefinitionType<Any>).getKotlinDescriptor()

        val defaultInvoke: String
        val default: String

        if(definition is IsWithDefaultDefinition<*> && definition.default != null) {
            val defaultValue = generateKotlinValue(definition, definition.default as Any, addImport)
            defaultInvoke = ", $defaultValue"
            default = " = $defaultValue"
        } else {
            defaultInvoke = ""
            default = ""
        }

        val nativeTypeName = names.kotlinTypeName(definition)

        for (import in names.getImports(definition)) {
            addImport(import)
        }

        propertiesKotlin.add(
            KotlinForProperty(
                name = it.name,
                index = it.index,
                value = """val ${it.name}: $nativeTypeName$default""",
                definition = names.definitionToKotlin(definition, addImport),
                invoke = "map(${it.index}$defaultInvoke)"
            )
        )
    }
    return propertiesKotlin
}
