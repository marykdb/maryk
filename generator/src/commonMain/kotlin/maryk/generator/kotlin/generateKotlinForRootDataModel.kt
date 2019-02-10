package maryk.generator.kotlin

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.TypeId
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference

fun <P: PropertyDefinitions> RootDataModel<*, P>.generateKotlin(
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
    val keyDefAsKotlin = if (this.keyDefinitions.size != 1 || this.keyDefinitions[0] != UUIDKey) {
        val keyDefs = this.keyDefinitions.generateKotlin(addImport)

        """keyDefinitions = arrayOf(
            ${keyDefs.prependIndent().prependIndent().trimStart()}
        ),
        """.trimStart()
    } else ""

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = this.properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    object $name: RootDataModel<$name, $name.Properties>(
        name = "$name",
        ${keyDefAsKotlin}properties = Properties
    ) {
        object Properties: PropertyDefinitions() {
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
private fun Array<out IsFixedBytesPropertyReference<out Any>>.generateKotlin(addImport: (String) -> Unit): String {
    val output = mutableListOf<String>()

    for (keyPart in this) {
        when (keyPart) {
            is UUIDKey -> {
                addImport("maryk.core.properties.definitions.key.UUIDKey")
                output += "UUIDKey"
            }
            is TypeId<*> -> {
                addImport("maryk.core.properties.definitions.key.TypeId")
                @Suppress("UNCHECKED_CAST")
                val typeId= keyPart as TypeId<IndexedEnum<Any>>
                output += "TypeId(Properties.${typeId.reference.name}.getRef())"
            }
            is Reversed<*> -> {
                addImport("maryk.core.properties.definitions.key.Reversed")
                @Suppress("UNCHECKED_CAST")
                val reversed: Reversed<Any> = keyPart as Reversed<Any>
                output += "Reversed(Properties.${reversed.reference.name}.getRef())"
            }
            is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
                output += "Properties.${keyPart.name}"
            }
            else -> throw Exception("Unknown key part type $keyPart")
        }
    }

    return output.joinToString(",\n").prependIndent()
}
