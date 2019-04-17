package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.models.IsNamedDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference

fun RootDataModel<*, *>.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.RootDataModel",
        "maryk.core.properties.PropertyDefinitions"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    // Add key definitions if they are not the default UUID key
    val keyDefAsKotlin = if (this.keyDefinition != UUIDKey) {
        val keyDefs = this.keyDefinition.generateKotlin(packageName, name, addImport)

        """keyDefinitions = ${keyDefs.prependIndent().prependIndent().trimStart()},
        """.trimStart()
    } else ""

    // Add indices if they are not null
    val indicesAsKotlin = this.indices?.let { indexables ->
        val output = mutableListOf<String>()
        for (it in indexables) {
            output += it.generateKotlin(packageName, name, addImport)
        }
        "indices = listOf(\n${output.joinToString(",\n").prependIndent().prependIndent().prependIndent()}\n        ),\n        "
    } ?: ""

    val reservedIndices = this.reservedIndices.let { indices ->
        when {
            indices.isNullOrEmpty() -> ""
            else -> "reservedIndices = listOf(${indices.joinToString(", ")}),\n        "
        }
    }
    val reservedNames = this.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> ""
            else -> "reservedNames = listOf(${names.joinToString(", ", "\"", "\"")}),\n        "
        }
    }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = this.properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    object $name : RootDataModel<$name, $name.Properties>(
        $keyDefAsKotlin$indicesAsKotlin$reservedIndices${reservedNames}properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            ${propertiesKotlin.generateDefinitionsForProperties().prependIndent().trimStart()}
        }

        operator fun invoke(
            ${propertiesKotlin.generateValuesForProperties().prependIndent().prependIndent().prependIndent().trimStart()}
        ) = values {
            mapNonNulls(
                ${propertiesKotlin.generateAssignsForProperties().prependIndent().prependIndent().prependIndent().prependIndent().trimStart()}
            )
        }
    }
    """.trimIndent()

    writeKotlinFile(packageName, importsToAdd, enumKotlinDefinitions, code, writer)
}

/**
 * Generate the kotlin for key definitions and adds imports with [addImport]
 */
private fun IsIndexable.generateKotlin(
    packageName: String,
    name: String,
    addImport: (String) -> Unit
): String = when (this) {
    is UUIDKey -> {
        addImport("maryk.core.properties.definitions.key.UUIDKey")
        "UUIDKey"
    }
    is TypeReference<*, *> -> {
        val typeId = this
        val parentReference = (typeId.parentReference as IsPropertyReferenceForValues<*, *, *, *>)
        parentReference.generateRef(packageName, name, addImport, refFunction = "typeRef")
    }
    is Reversed<*> -> {
        addImport("maryk.core.properties.definitions.key.Reversed")
        "Reversed(${this.reference.generateRef(packageName, name, addImport)})"
    }
    is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
        generateRef(packageName, name, addImport)
    }
    is ValueWithFlexBytesPropertyReference<*, *, *, *> -> {
        generateRef(packageName, name, addImport)
    }
    is Multiple -> {
        addImport("maryk.core.properties.definitions.key.Multiple")
        val output = mutableListOf<String>()

        for (it in this.references) {
            output += it.generateKotlin(packageName, name, addImport)
        }

        "Multiple(\n${output.joinToString(",\n").prependIndent()}\n)"
    }
    else -> throw TypeException("Unknown IsIndexable type: $this")
}

/**
 * Generate reference variable for indexable using [packageName] and [modelName] for [addImport]
 * [refFunction] can be overridden for other types of ref
 */
private fun IsPropertyReferenceForValues<*, *, *, *>.generateRef(
    packageName: String,
    modelName: String,
    addImport: (String) -> Unit,
    refFunction: String = "ref"
): String {
    var parent = ""
    this.parentReference?.let {
        if (it is IsPropertyReferenceForValues<*, *, *, *>) {
            if (it.propertyDefinition is IsEmbeddedDefinition<*, *>) {
                val embedModelName = ((it.propertyDefinition as IsEmbeddedDefinition<*, *>).dataModel as IsNamedDataModel<*>).name
                addImport("$packageName.$embedModelName.Properties.${this.name}")
            }

            parent = it.generateRef(packageName, modelName, addImport)
        }
    }
    if (parent.isEmpty()) {
        addImport("$packageName.$modelName.Properties.${this.name}")
    }
    return "${this.name}.$refFunction($parent)"
}
