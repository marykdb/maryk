package maryk.generator.kotlin

import maryk.core.models.DataModel

fun DataModel<*, *>.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.DataModel",
        "maryk.core.properties.PropertyDefinitions"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    object $name : DataModel<$name, $name.Properties>(
        properties = Properties
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

internal fun List<KotlinForProperty>.generateDefinitionsForProperties(): String {
    var properties = ""
    for (it in this) {
        properties += """
        val ${it.name} = add(
            index = ${it.index}, name = "${it.name}",
            definition = ${it.definition.prependIndent().prependIndent().prependIndent().trimStart()}
        )"""
    }
    return properties
}

internal fun List<KotlinForProperty>.generateValuesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (properties.isNotEmpty()) properties += ",\n"
        properties += it.value
    }
    return properties
}

internal fun List<KotlinForProperty>.generateAssignsForProperties(): String {
    var properties = ""
    for (it in this) {
        if (properties.isNotEmpty()) properties += ",\n"
        properties += it.assign
    }
    return properties
}
