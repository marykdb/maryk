package maryk.generator.kotlin

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Generates kotlin code to [writer] for IndexedEnumDefinition in [packageName]
 */
fun <E: IndexedEnum<E>> IndexedEnumDefinition<E>.generateKotlin(packageName: String, writer: (String) -> Unit) {
    val importsToAdd = mutableSetOf(
        "maryk.core.properties.enum.IndexedEnum",
        "maryk.core.properties.enum.IndexedEnumDefinition"
    )

    val values = mutableListOf<String>()

    for (value in this.values()) {
        values.add("${value.name}(${value.index})")
    }

    val code = """
    enum class ${this.name}(
        override val index: Int
    ): IndexedEnum<${this.name}> {
        ${values.joinToString(",\n").prependIndent().prependIndent().trimStart()};

        companion object: IndexedEnumDefinition<${this.name}>(
            "${this.name}", ${this.name}::values
        )
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
