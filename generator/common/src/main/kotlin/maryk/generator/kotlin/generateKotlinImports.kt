package maryk.generator.kotlin

/**
 * Generates kotlin code for given [imports]
 */
internal fun generateImports(imports: Set<String>): String {
    var allImports = ""
    for (it in imports.sorted()) {
        allImports += "import $it\n"
    }
    return allImports
}
