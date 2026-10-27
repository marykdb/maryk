package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition

internal fun IsTypedDataModel<*>.generateKotlin(
    addImport: (String) -> Unit,
    generationContext: GenerationContext? = null,
    addEnumDefinition: ((String) -> Unit)? = null
): List<KotlinForProperty> {
    val propertiesKotlin = mutableListOf<KotlinForProperty>()
    val context = generationContext ?: GenerationContext()
    val activeTypeEnums = mutableSetOf<String>()
    for (propertyDefinitionWrapper in this) {
        @Suppress("UNCHECKED_CAST")
        val definition = propertyDefinitionWrapper.definition as? IsTransportablePropertyDefinitionType<Any>
            ?: throw TypeException("Property definition is not supported: ${propertyDefinitionWrapper.definition}")

        val kotlinDescriptor =
            definition.getKotlinDescriptor()

        if (addEnumDefinition != null) {
            propertyDefinitionWrapper.definition.collectKotlinEnums(context, addImport, addEnumDefinition, activeTypeEnums)
        }

        val default = if (definition is HasDefaultValueDefinition<*> && definition.default != null) {
            val kotlinValue = (definition as? NumberDefinition<*>)?.let { numberDefinition ->
                generateKotlinNumberValue(numberDefinition, definition.default as Any)
            } ?: generateKotlinValue(definition, definition.default as Any, addImport)
            " = $kotlinValue"
        } else if (!definition.required) {
            "? = null"
        } else {
            ""
        }

        val nativeTypeName = ": "+kotlinDescriptor.kotlinTypeName(definition)

        for (import in kotlinDescriptor.getImports(definition)) {
            addImport(import)
        }

        propertiesKotlin.add(
            KotlinForProperty(
                name = propertyDefinitionWrapper.name.kotlinIdentifier(),
                wrapName = kotlinDescriptor.wrapFunctionName,
                index = propertyDefinitionWrapper.index,
                altNames = propertyDefinitionWrapper.alternativeNames,
                value = "${propertyDefinitionWrapper.name.kotlinIdentifier()}$nativeTypeName$default",
                definition = kotlinDescriptor.definitionToKotlinFields(
                    definition,
                    addImport,
                    (propertyDefinitionWrapper as? IsSensitiveValueDefinitionWrapper<*, *, *, *>)?.sensitive == true,
                ),
                invoke = "values(${propertyDefinitionWrapper.index}u)"
            )
        )
    }
    return propertiesKotlin
}

private fun IsSerializablePropertyDefinition<*, *>.collectKotlinEnums(
    context: GenerationContext,
    addImport: (String) -> Unit,
    addEnumDefinition: (String) -> Unit,
    activeTypeEnums: MutableSet<String>,
) {
    fun addEnum(enum: IsIndexedEnumDefinition<*>) {
        val existing = context.enums.firstOrNull { it.name == enum.name }
        if (existing != null) {
            require(existing.kotlinEnumSource {} == enum.kotlinEnumSource {}) {
                "Conflicting definitions for enum ${enum.name}"
            }
        } else {
            addEnumDefinition(enum.kotlinEnumSource(addImport))
            context.enums.add(enum)
        }
    }

    when (this) {
        is EnumDefinition<*> -> addEnum(enum)
        is MultiTypeDefinition<*, *> -> {
            addEnum(typeEnum)
            if (activeTypeEnums.add(typeEnum.name)) {
                try {
                    typeEnum.cases().forEach { case ->
                        (case.definition as? IsSerializablePropertyDefinition<*, *>)?.collectKotlinEnums(
                            context, addImport, addEnumDefinition, activeTypeEnums,
                        )
                    }
                } finally {
                    activeTypeEnums.remove(typeEnum.name)
                }
            }
        }
        is IsCollectionDefinition<*, *, *, *> -> valueDefinition.collectKotlinEnums(
            context, addImport, addEnumDefinition, activeTypeEnums,
        )
        is IsMapDefinition<*, *, *> -> {
            keyDefinition.collectKotlinEnums(context, addImport, addEnumDefinition, activeTypeEnums)
            valueDefinition.collectKotlinEnums(context, addImport, addEnumDefinition, activeTypeEnums)
        }
    }
}

private fun IsIndexedEnumDefinition<*>.kotlinEnumSource(addImport: (String) -> Unit): String = when (this) {
    is IndexedEnumDefinition<*> -> generateKotlinClass(addImport)
    is MultiTypeEnumDefinition<*> -> generateKotlinClass(addImport)
    else -> throw TypeException("Enum definition is not supported: $name")
}
