package maryk.generator.kotlin

import maryk.core.objects.ValueDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.ValueDataObject

fun <DO: ValueDataObject, P: PropertyDefinitions<DO>> ValueDataModel<DO, P>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf(
        "maryk.core.objects.ValueDataModel",
        "maryk.core.properties.definitions.PropertyDefinitions",
        "maryk.core.properties.types.ValueDataObject"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val propertiesKotlin = properties.generateKotlin(addImport)

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
            override fun invoke(map: Map<Int, *>) = $name(
                ${propertiesKotlin.generateInvokesForProperties().prependIndent().prependIndent().prependIndent().trimStart()}
            )
        }
    }
    """.trimIndent()

    val imports = """
    package $packageName

    ${generateImports(
        importsToAdd
    ).prependIndent().trimStart()}
    """.trimIndent()

    writer("$imports\n$code")
}

private fun List<KotlinForProperty>.generatePropertyNamesForConstructor(): String {
    val properties = mutableListOf<String>()
    for (it in this) {
        properties.add(it.name)
    }
    return properties.joinToString(", ")
}
