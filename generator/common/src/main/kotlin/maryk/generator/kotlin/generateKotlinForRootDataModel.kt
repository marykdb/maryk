package maryk.generator.kotlin

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.TypeId
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum

fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.generateKotlin(
    packageName: String,
    generationContext: KotlinGenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.RootDataModel",
        "maryk.core.objects.ValueMap",
        "maryk.core.properties.definitions.PropertyDefinitions"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    // Add key definitions if they are not the default UUID key
    val keyDefAsKotlin = if (this.key.keyDefinitions.size != 1 || this.key.keyDefinitions[0] != UUIDKey) {
        val keyDefs = this.key.keyDefinitions.generateKotlin(addImport)

        addImport("maryk.core.models.definitions")

        """keyDefinitions = definitions(
            ${keyDefs.prependIndent().prependIndent().trimStart()}
        ),
            """.prependIndent().trimStart()
    } else ""

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    data class $name(
        ${propertiesKotlin.generateValuesForProperties().prependIndent().prependIndent().trimStart()}
    ) {
        object Properties: PropertyDefinitions<$name>() {
            ${propertiesKotlin.generateDefinitionsForProperties(modelName = name).prependIndent().trimStart()}
        }

        companion object: RootDataModel<$name, Properties>(
            name = "$name",
            ${keyDefAsKotlin}properties = Properties
        ) {
            override fun invoke(map: ValueMap<$name>) = $name(
                ${propertiesKotlin.generateInvokesForProperties().prependIndent().prependIndent().prependIndent().trimStart()}
            )
        }
    }
    """.trimIndent()

    writeKotlinFile(packageName, importsToAdd, enumKotlinDefinitions, code, writer)
}

/**
 * Generate the kotlin for key definitions and adds imports with [addImport]
 */
private fun Array<out FixedBytesProperty<out Any>>.generateKotlin(addImport: (String) -> Unit): String {
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
            is IsPropertyDefinitionWrapper<*, *, *, *> -> {
                output += "Properties.${keyPart.name}"
            }
            else -> throw Exception("Unknown key part type $keyPart")
        }
    }

    return output.joinToString(",\n").prependIndent()
}
