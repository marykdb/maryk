package maryk.datastore.terminal

import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
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
    val details = buildString {
        append(indent)
        append("- ")
        append(wrapper.index)
        append(": ")
        append(wrapper.name)
        append(" (")
        append(definition::class.simpleName ?: definition::class.qualifiedName)
        append(')')
        val flags = mutableListOf<String>()
        if (definition.required) flags += "required"
        if (definition.final) flags += "final"
        if (flags.isNotEmpty()) {
            append(" [")
            append(flags.joinToString(", "))
            append(']')
        }
        when (definition) {
            is IsListDefinition<*, *> -> {
                append(" element=")
                append(definition.valueDefinition::class.simpleName ?: definition.valueDefinition::class.qualifiedName)
            }
            is IsSetDefinition<*, *> -> {
                append(" element=")
                append(definition.valueDefinition::class.simpleName ?: definition.valueDefinition::class.qualifiedName)
            }
            is IsMapDefinition<*, *, *> -> {
                append(" key=")
                append(definition.keyDefinition::class.simpleName ?: definition.keyDefinition::class.qualifiedName)
                append(", value=")
                append(definition.valueDefinition::class.simpleName ?: definition.valueDefinition::class.qualifiedName)
            }
        }
    }

    val lines = mutableListOf(details)
    if (wrapper.definition is IsEmbeddedDefinition<*>) {
        val nested = (wrapper.definition as IsEmbeddedDefinition<*>).dataModel
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
