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

    val reservedIndices = this.reservedIndices.let { indices ->
        when {
            indices.isNullOrEmpty() -> ""
            else -> "reservedIndices = listOf(${indices.joinToString(", ", postfix = "u")}),\n        "
        }
    }
    val reservedNames = this.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> ""
            else -> "reservedNames = listOf(${names.joinToString(", ", "\"", "\"")}),\n        "
        }
    }

    val code = """
    object $name : DataModel<$name, $name.Properties>(
        $reservedIndices${reservedNames}properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            ${propertiesKotlin.generateDefinitionsForProperties(addImport).prependIndent().trimStart()}
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

internal fun List<KotlinForProperty>.generateDefinitionsForProperties(addImport: (String) -> Unit): String {
    var properties = ""
    for (it in this) {
        addImport("maryk.core.properties.definitions."+it.wrapName)

        val altNames = it.altNames?.let { altName ->
            "\n            alternativeNames = setOf(${altName.joinToString(", ") { """"$it"""" }}),"
        } ?: ""
        val definitionProperties = "\n            " +it.definition.prependIndent().prependIndent().prependIndent().trimStart()
        val propertiesToBeAdded = if (definitionProperties.isBlank() && altNames.isEmpty()) "" else ",$altNames$definitionProperties"

        properties += """
        val ${it.name} by ${it.wrapName}(
            index = ${it.index}u$propertiesToBeAdded
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
