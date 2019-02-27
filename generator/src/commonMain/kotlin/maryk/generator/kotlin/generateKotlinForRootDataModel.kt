package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference

fun <P : PropertyDefinitions> RootDataModel<*, P>.generateKotlin(
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

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = this.properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    object $name : RootDataModel<$name, $name.Properties>(
        name = "$name",
        ${keyDefAsKotlin}${indicesAsKotlin}properties = Properties
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
        @Suppress("UNCHECKED_CAST")
        val typeId = this as TypeReference<IndexedEnum<Any>, IsPropertyContext>
        val parentReference = (typeId.parentReference as IsPropertyReferenceForValues<*, *, *, *>)
        addImport("$packageName.$name.Properties.${parentReference.name}")
        "${parentReference.name}.typeRef()"
    }
    is Reversed<*> -> {
        addImport("maryk.core.properties.definitions.key.Reversed")
        @Suppress("UNCHECKED_CAST")
        val reversed: Reversed<Any> = this as Reversed<Any>
        addImport("$packageName.$name.Properties.${reversed.reference.name}")
        "Reversed(${reversed.reference.name}.ref())"
    }
    is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
        addImport("$packageName.$name.Properties.${this.name}")
        "${this.name}.ref()"
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
