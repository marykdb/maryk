package maryk.generator.kotlin

import maryk.core.models.IsValueDataModel

fun IsValueDataModel<*, *>.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.values.ObjectValues",
        "maryk.core.properties.ValueModel",
        "maryk.core.properties.types.ValueDataObject"
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val code = """
    data class ${Model.name}(
        ${propertiesKotlin.generateObjectValuesForProperties().prependIndent().prependIndent().trimStart()}
    ) : ValueDataObject(toBytes(${propertiesKotlin.generatePropertyNamesForConstructor()})) {
        companion object : ValueModel<${Model.name}, Companion>(${Model.name}::class) {
            ${propertiesKotlin.generateDefinitionsForObjectProperties(modelName = Model.name, addImport = addImport).prependIndent().trimStart()}

            override fun invoke(values: ObjectValues<${Model.name}, Companion>) = ${Model.name}(
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
