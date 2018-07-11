package maryk.generator.kotlin

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions

fun <DO: Any, P: ObjectPropertyDefinitions<DO>> ObjectDataModel<DO, P>.generateKotlin(
    packageName: String,
    generationContext: KotlinGenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.ObjectDataModel",
        "maryk.core.objects.ObjectValues",
        "maryk.core.properties.ObjectPropertyDefinitions"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    data class $name(
        ${propertiesKotlin.generateValuesForProperties().prependIndent().prependIndent().trimStart()}
    ) {
        object Properties: ObjectPropertyDefinitions<$name>() {
            ${propertiesKotlin.generateDefinitionsForProperties(modelName = name).prependIndent().trimStart()}
        }

        companion object: ObjectDataModel<$name, Properties>(
            name = "$name",
            properties = Properties
        ) {
            override fun invoke(map: ObjectValues<$name, Properties>) = $name(
                ${propertiesKotlin.generateInvokesForProperties().prependIndent().prependIndent().prependIndent().trimStart()}
            )
        }
    }
    """.trimIndent()

    writeKotlinFile(packageName, importsToAdd, enumKotlinDefinitions, code, writer)
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
