package maryk.generator.kotlin

/**
 * Generate a Kotlin file with code within [packageName],
 * with imports in [importsToAdd],
 * [enumKotlinDefinitions] to write at top,
 * and [code] to write to [writer]
 */
internal fun writeKotlinFile(
    packageName: String,
    importsToAdd: MutableSet<String>,
    enumKotlinDefinitions: MutableList<String>?,
    code: String,
    writer: (String) -> Unit
) {
    val enumDefinitionKotlin = if (enumKotlinDefinitions != null && enumKotlinDefinitions.isNotEmpty()) {
        "\n" + enumKotlinDefinitions.joinToString("\n\n") + '\n'
    } else ""

    val imports = """
    package $packageName

    ${generateImports(
        importsToAdd
    ).prependIndent().trimStart()}
    """.trimIndent()

    writer("$imports$enumDefinitionKotlin\n$code")
}
