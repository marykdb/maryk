package maryk.generator.kotlin

import maryk.core.models.DataModel

fun DataModel<*>.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.properties.DataModel",
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val reservedIndices = Meta.reservedIndices.let { indexes ->
        when {
            indexes.isNullOrEmpty() -> null
            else -> "reservedIndices = listOf(${indexes.joinToString(", ", postfix = "u")}),"
        }
    }
    val reservedNames = Meta.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> null
            else -> "reservedNames = listOf(${names.joinToString(", ", "\"", "\"")}),"
        }
    }

    val constructorParameters = arrayOf(reservedIndices, reservedNames)
        .filterNotNull()
        .joinToString("\n        ")
        .let { if (it.isBlank()) "" else "\n        $it\n    " }

    val code = """
    object ${Meta.name} : DataModel<${Meta.name}>($constructorParameters) {
        ${propertiesKotlin.generateDefinitionsForProperties(addImport).trimStart()}
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
