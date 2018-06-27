package maryk.generator.kotlin

import maryk.core.models.ValueDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.ValueDataObject

fun <DO: ValueDataObject, P: PropertyDefinitions<DO>> ValueDataModel<DO, P>.generateKotlin(
    packageName: String,
    generationContext: KotlinGenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.ValueDataModel",
        "maryk.core.objects.ValueMap",
        "maryk.core.properties.definitions.PropertyDefinitions",
        "maryk.core.properties.types.ValueDataObject"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    data class $name(
        ${propertiesKotlin.generateValuesForProperties().prependIndent().prependIndent().trimStart()}
    ): ValueDataObject(toBytes(${propertiesKotlin.generatePropertyNamesForConstructor()})) {
        object Properties: PropertyDefinitions<$name>() {
            ${propertiesKotlin.generateDefinitionsForProperties(modelName = name).prependIndent().trimStart()}
        }

        companion object: ValueDataModel<$name, Properties>(
            name = "$name",
            properties = Properties
        ) {
            override fun invoke(map: ValueMap<$name>) = $name(
                ${propertiesKotlin.generateInvokesForProperties().prependIndent().prependIndent().prependIndent().trimStart()}
            )
        }
    }
    """.trimIndent()

    writeKotlinFile(packageName, importsToAdd, enumKotlinDefinitions, code, writer)
}

private fun List<KotlinForProperty>.generatePropertyNamesForConstructor(): String {
    val properties = mutableListOf<String>()
    for (it in this) {
        properties.add(it.name)
    }
    return properties.joinToString(", ")
}
