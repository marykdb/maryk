package maryk.generator.kotlin

import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.ValueDataObject

fun <DO : ValueDataObject, P : ObjectPropertyDefinitions<DO>> ValueDataModel<DO, P>.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.models.ValueDataModel",
        "maryk.core.values.ObjectValues",
        "maryk.core.properties.ObjectPropertyDefinitions",
        "maryk.core.properties.types.ValueDataObject"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = properties.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    data class $name(
        ${propertiesKotlin.generateObjectValuesForProperties().prependIndent().prependIndent().trimStart()}
    ) : ValueDataObject(toBytes(${propertiesKotlin.generatePropertyNamesForConstructor()})) {
        object Properties : ObjectPropertyDefinitions<$name>() {
            ${propertiesKotlin.generateDefinitionsForObjectProperties(modelName = name).prependIndent().trimStart()}
        }

        companion object : ValueDataModel<$name, Properties>(
            name = "$name",
            properties = Properties
        ) {
            override fun invoke(values: ObjectValues<$name, Properties>) = $name(
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

private fun List<KotlinForProperty>.generateObjectValuesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += "val ${it.value}"
    }
    return properties
}

private fun List<KotlinForProperty>.generateInvokesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (!properties.isEmpty()) properties += ",\n"
        properties += """${it.name} = ${it.invoke}"""
    }
    return properties.prependIndent()
}

private fun List<KotlinForProperty>.generateDefinitionsForObjectProperties(modelName: String): String {
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
