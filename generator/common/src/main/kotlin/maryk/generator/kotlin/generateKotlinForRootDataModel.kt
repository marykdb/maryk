package maryk.generator.kotlin

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions

fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf(
        "maryk.core.objects.RootDataModel",
        "maryk.core.properties.definitions.PropertyDefinitions"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val propertiesKotlin = properties.generateKotlin(addImport)

    val code = """
    data class $name(
        ${propertiesKotlin.generateValuesForProperties().prependIndent().prependIndent().trimStart()}
    ) {
        object Properties: PropertyDefinitions<$name>() {
            ${propertiesKotlin.generateDefinitionsForProperties(modelName = name).prependIndent().trimStart()}
        }

        companion object: RootDataModel<$name, Properties>(
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

    ${generateDefinitionImports(
        importsToAdd
    ).prependIndent().trimStart()}
    """.trimIndent()

    writer("$imports\n$code")
}

private fun generateDefinitionImports(imports: Set<String>): String {
    var allImports = ""
    for (it in imports.sorted()) {
        allImports += "import $it\n"
    }
    return allImports
}

private fun List<KotlinForProperty>.generateInvokesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += """${it.name} = ${it.invoke}"""
    }
    return properties.prependIndent()
}

private fun List<KotlinForProperty>.generateDefinitionsForProperties(modelName: String): String {
    var properties = ""
    for (it in this) {
        properties += """
        val ${it.name} = add(
            index = ${it.index}, name = "${it.name}",
            definition = ${it.definition.prependIndent().prependIndent().prependIndent().trimStart()},
            getter = $modelName::${it.name}
        )"""
    }
    return properties
}

private fun List<KotlinForProperty>.generateValuesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += it.value
    }
    return properties
}
