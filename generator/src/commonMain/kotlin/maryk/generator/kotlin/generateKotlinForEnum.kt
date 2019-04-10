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
    addImport("maryk.core.properties.enum.IndexedEnumImpl")
    addImport("maryk.core.properties.enum.IndexedEnumDefinition")

    return """
    sealed class ${this.name}(
        index: UInt
    ) : IndexedEnumImpl<${this.name}>(index) {
        ${this.cases().joinToString("") {
            "object ${it.name}: ${this.name}(${it.index}u)\n"
        }.prependIndent().prependIndent().trimStart()}
        class Unknown${this.name}(index: UInt, override val name: String): ${this.name}(index)

        companion object : IndexedEnumDefinition<${this.name}>(
            ${this.name}::class, { arrayOf(${this.cases().joinToString(", ") { it.name }}) }, unknownCreator = ::Unknown${this.name}
        )
    }
    """.trimIndent()
}
