package maryk.generator.kotlin

import maryk.core.objects.DataModel
import maryk.core.properties.definitions.PropertyDefinitions

fun <DO: Any, P: PropertyDefinitions<DO>> DataModel<DO, P>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf(
        "maryk.core.objects.DataModel",
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

        companion object: DataModel<$name, Properties>(
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

internal fun List<KotlinForProperty>.generateInvokesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += """${it.name} = ${it.invoke}"""
    }
    return properties.prependIndent()
}

internal fun List<KotlinForProperty>.generateDefinitionsForProperties(modelName: String): String {
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

internal fun List<KotlinForProperty>.generateValuesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += it.value
    }
    return properties
}
