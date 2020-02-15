package maryk.generator.kotlin

import maryk.core.models.ValueDataModel

fun ValueDataModel<*, *>.generateKotlin(
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
            ${propertiesKotlin.generateDefinitionsForObjectProperties(modelName = name, addImport = addImport).prependIndent().trimStart()}
        }

        companion object : ValueDataModel<$name, Properties>(
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
        if (properties.isNotEmpty()) properties += ",\n"
        properties += "val ${it.value}"
    }
    return properties
}

private fun List<KotlinForProperty>.generateInvokesForProperties(): String {
    var properties = ""
    for (it in this) {
        if (properties.isNotEmpty()) properties += ",\n"
        properties += """${it.name} = ${it.invoke}"""
    }
    return properties.prependIndent()
}

private fun List<KotlinForProperty>.generateDefinitionsForObjectProperties(
    modelName: String,
    addImport: (String) -> Unit
): String {
    var properties = ""
    for (it in this) {
        addImport("maryk.core.properties.definitions."+it.wrapName)
        properties += """
        val ${it.name} by ${it.wrapName}(
            index = ${it.index}u,
            getter = $modelName::${it.name},
            ${it.definition.prependIndent().prependIndent().prependIndent().trimStart()}
        )"""
    }
    return properties
}
