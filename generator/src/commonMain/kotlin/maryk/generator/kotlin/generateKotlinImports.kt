package maryk.generator.kotlin

/**
 * Generates kotlin code for given [imports]
 */
internal fun generateImports(imports: Set<String>): String =
    imports.sorted().joinToString(separator = "") { "import $it\n" }
