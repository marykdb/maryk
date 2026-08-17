package maryk.generator.kotlin

import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Generates kotlin code to [writer] for IndexedEnumDefinition in [packageName]
 */
fun IndexedEnumDefinition<*>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf<String>()

    val code = this.generateKotlinClass {
        importsToAdd.add(it)
    }

    writeKotlinFile(packageName, importsToAdd, null, code, writer)
}

/** Generates kotlin class string for IndexedEnumDefinition and adds imports to [addImport] */
fun IndexedEnumDefinition<*>.generateKotlinClass(addImport: (String) -> Unit): String {
    validateKotlinGeneratedEnumNames(setOf("IndexedEnumDefinition", "IndexedEnumImpl"))
    addImport("maryk.core.properties.enum.IndexedEnumImpl")
    addImport("maryk.core.properties.enum.IndexedEnumDefinition")

    val reservedIndices = this.reservedIndices.let { indexes ->
        when {
            indexes.isNullOrEmpty() -> ""
            else -> "\n"+ "reservedIndices = listOf(${indexes.toKotlinUIntList()}),".prependIndent().prependIndent().prependIndent()
        }
    }
    val reservedNames = this.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> ""
            else -> "\n"+ "reservedNames = listOf(${names.joinToString(", ") { it.kotlinStringLiteral() }}),".prependIndent().prependIndent().prependIndent()
        }
    }

    return """
    sealed class ${this.name.kotlinIdentifier()}(
        index: UInt,
        alternativeNames: Set<String>? = null
    ) : IndexedEnumImpl<${this.name.kotlinIdentifier()}>(index, alternativeNames) {
        ${this.cases().joinToString("") { case ->
        val alternativeNames = case.alternativeNames?.let {
                ", setOf(${it.joinToString(", ") { name -> name.kotlinStringLiteral() } })"
            } ?: ""
            "object ${case.name.kotlinIdentifier()}: ${this.name.kotlinIdentifier()}(${case.index}u$alternativeNames)\n"
        }.prependIndent().prependIndent().trimStart()}
        class ${"Unknown${this.name}".kotlinIdentifier()}(index: UInt, override val name: String): ${this.name.kotlinIdentifier()}(index)

        companion object : IndexedEnumDefinition<${this.name.kotlinIdentifier()}>(
            ${this.name.kotlinIdentifier()}::class,
            values = { listOf(${this.cases().joinToString(", ") { it.name.kotlinIdentifier() }}) },$reservedIndices$reservedNames
            unknownCreator = ::${"Unknown${this.name}".kotlinIdentifier()}
        )
    }
    """.trimIndent()
}
