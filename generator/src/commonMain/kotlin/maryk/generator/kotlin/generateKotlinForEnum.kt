package maryk.generator.kotlin

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Generates kotlin code to [writer] for IndexedEnumDefinition in [packageName]
 */
fun <E: IndexedEnum<E>> IndexedEnumDefinition<E>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf<String>()

    val code = this.generateKotlinClass {
        importsToAdd.add(it)
    }

    writeKotlinFile(packageName, importsToAdd, null, code, writer)
}

/** Generates kotlin class string for IndexedEnumDefinition and adds imports to [addImport] */
fun <E: IndexedEnum<E>> IndexedEnumDefinition<E>.generateKotlinClass(addImport: (String) -> Unit): String {
    addImport("maryk.core.properties.enum.IndexedEnum")
    addImport("maryk.core.properties.enum.IndexedEnumDefinition")

    val values = mutableListOf<String>()
    for (value in this.cases()) {
        values.add("${value.name}(${value.index}u)")
    }

    return """
    enum class ${this.name}(
        override val index: UInt
    ): IndexedEnum<${this.name}> {
        ${values.joinToString(",\n").prependIndent().prependIndent().trimStart()};

        companion object: IndexedEnumDefinition<${this.name}>(
            "${this.name}", ${this.name}::values
        )
    }
    """.trimIndent()
}
