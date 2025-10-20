package maryk.datastore.terminal

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.HasSizeDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.datastore.terminal.driver.StoredModel

fun renderModelDefinition(model: StoredModel): List<String> {
    val lines = mutableListOf<String>()
    lines += "Model ${model.name} (version ${model.version})"
    val meta = model.definition.Meta
    lines += "  Key definition: ${meta.keyDefinition::class.simpleName ?: meta.keyDefinition}".trimEnd()
    meta.indexes?.takeIf { it.isNotEmpty() }?.let { indexes ->
        lines += "  Indexes:"
        indexes.forEach { index -> lines += "    - $index" }
    }
    meta.reservedNames?.takeIf { it.isNotEmpty() }?.let { names ->
        lines += "  Reserved names: ${names.joinToString()}"
    }
    meta.reservedIndices?.takeIf { it.isNotEmpty() }?.let { indices ->
        lines += "  Reserved indices: ${indices.joinToString()}"
    }
    lines += "  Properties:"
    if (!model.definition.iterator().hasNext()) {
        lines += "    <none>"
    } else {
        for (property in model.definition) {
            lines.addAll(describeProperty(property, "    "))
        }
    }
    return lines
}

private fun describeProperty(
    wrapper: IsDefinitionWrapper<*, *, IsPropertyContext, *>,
    indent: String,
): List<String> {
    val definition = wrapper.definition
    val summary = buildString {
        append(indent)
        append("- ")
        append(wrapper.index)
        append(": ")
        append(wrapper.name)
        append(" (")
        append(definition.documentationTypeName())
        append(')')
    }

    val lines = mutableListOf(summary)
    collectAttributes(definition)?.forEach { attribute ->
        lines += "$indent    $attribute"
    }

    if (definition is IsListDefinition<*, *>) {
        lines += "$indent    element type: ${definition.valueDefinition.documentationTypeName()}"
    }
    if (definition is IsSetDefinition<*, *>) {
        lines += "$indent    element type: ${definition.valueDefinition.documentationTypeName()}"
    }
    if (definition is IsMapDefinition<*, *, *>) {
        lines += "$indent    key type: ${definition.keyDefinition.documentationTypeName()}"
        lines += "$indent    value type: ${definition.valueDefinition.documentationTypeName()}"
    }

    if (definition is IsEmbeddedDefinition<*>) {
        val nested = definition.dataModel
        if (nested.iterator().hasNext()) {
            for (child in nested) {
                lines.addAll(describeProperty(child, "$indent    "))
            }
        } else {
            lines += "$indent    <empty embedded model>"
        }
    }
    return lines
}

private fun collectAttributes(definition: IsPropertyDefinition<*>): List<String>? {
    val attributes = mutableListOf<String>()

    if (!definition.required) {
        attributes += "optional"
    }
    if (definition.final) {
        attributes += "final"
    }

    if (definition is IsComparableDefinition<*, *>) {
        if (definition.unique) {
            attributes += "unique"
        }
        definition.minValue?.let { attributes += "min value: $it" }
        definition.maxValue?.let { attributes += "max value: $it" }
    }

    if (definition is HasSizeDefinition) {
        definition.minSize?.let { attributes += "min size: $it" }
        definition.maxSize?.let { attributes += "max size: $it" }
    }

    if (definition is HasDefaultValueDefinition<*>) {
        definition.default?.let { attributes += "default: $it" }
    }

    when (definition) {
        is NumberDefinition<*> -> {
            attributes += "number type: ${definition.type.type.name}"
            if (definition.reversedStorage == true) {
                attributes += "reversed storage"
            }
        }
        is StringDefinition -> {
            definition.regEx?.let { attributes += "regex: $it" }
        }
        is EnumDefinition<*> -> {
            attributes += "enum: ${definition.enum.name}"
            @Suppress("UNCHECKED_CAST")
            val cases = definition.enum.cases().joinToString { enumCase -> (enumCase as IndexedEnum).name }
            if (cases.isNotBlank()) {
                attributes += "cases: $cases"
            }
        }
    }

    return attributes.takeIf { it.isNotEmpty() }
}

private fun IsPropertyDefinition<*>.documentationTypeName(): String {
    val transportable = this as? IsTransportablePropertyDefinitionType<*>
    val propertyType = transportable?.propertyDefinitionType
    val mapped = when (propertyType) {
        PropertyDefinitionType.Value -> "ValueObject"
        else -> propertyType?.name
    }
    return mapped ?: this::class.simpleName ?: this::class.qualifiedName ?: "Unknown"
}
