package maryk.generator.kotlin

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition

/**
 * Generates kotlin code to [writer] for IndexedEnumDefinition in [packageName]
 */
fun MultiTypeEnumDefinition<*>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf<String>()

    val code = this.generateKotlinClass {
        importsToAdd.add(it)
    }

    writeKotlinFile(packageName, importsToAdd, null, code, writer)
}

/** Generates kotlin class string for IndexedEnumDefinition and adds imports to [addImport] */
fun MultiTypeEnumDefinition<*>.generateKotlinClass(addImport: (String) -> Unit): String {
    addImport("maryk.core.properties.enum.IndexedEnumImpl")
    addImport("maryk.core.properties.enum.MultiTypeEnum")
    addImport("maryk.core.properties.enum.MultiTypeEnumDefinition")
    addImport("maryk.core.properties.definitions.IsUsableInMultiType")

    val reservedIndices = this.reservedIndices.let { indices ->
        when {
            indices.isNullOrEmpty() -> ""
            else -> "\n"+ "reservedIndices = listOf(${indices.joinToString(", ", postfix = "u")}),".prependIndent().prependIndent().prependIndent()
        }
    }
    val reservedNames = this.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> ""
            else -> "\n"+ "reservedNames = listOf(${names.joinToString(", ", "\"", "\"")}),".prependIndent().prependIndent().prependIndent()
        }
    }

    return """
    sealed class ${this.name}<T: Any>(
        index: UInt,
        override val definition: IsUsableInMultiType<T, *>?,
        alternativeNames: Set<String>? = null
    ) : IndexedEnumImpl<${this.name}<Any>>(index, alternativeNames), MultiTypeEnum<T> {
        ${@Suppress("UNCHECKED_CAST") (this.cases() as Array<MultiTypeEnum<Any>>).joinToString("") { case ->
            val alternativeNames = case.alternativeNames?.let { altNames ->
                ",\n    setOf(${altNames.joinToString(", ") { """"$it""""} })"
            } ?: ""
            val definition = case.definition
            require(definition is IsTransportablePropertyDefinitionType<*>) { "Property definition is not supported: ${this}" }
            val definitionDescriptor = definition.getKotlinDescriptor()
            addImport("maryk.core.properties.definitions.${definitionDescriptor.className}")
            definitionDescriptor.getImports(definition).forEach(addImport)
            val propertyDefinition = definitionDescriptor.definitionToKotlin(definition, addImport).prependIndent().trimStart(' ')
            "object ${case.name}: ${this.name}<${definitionDescriptor.kotlinTypeName(definition)}>(${case.index}u,$propertyDefinition$alternativeNames\n)\n"
        }.prependIndent().prependIndent().trimStart()}
        class Unknown${this.name}(index: UInt, override val name: String): ${this.name}<Any>(index, null)

        companion object : MultiTypeEnumDefinition<${this.name}<out Any>>(
            ${this.name}::class,
            values = { arrayOf(${this.cases().joinToString(", ") { it.name }}) },$reservedIndices$reservedNames
            unknownCreator = ::Unknown${this.name}
        )
    }
    """.trimIndent()
}
