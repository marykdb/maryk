package maryk.generator.proto3

fun generateProto3FileHeader(
    packageName: String,
    protosToImport: List<String> = listOf(),
    writer: (String) -> Unit
) {
    val imports = if (protosToImport.isNotEmpty()) {
        "\n" + protosToImport.joinToString(separator = "\n") { """import "$it.proto";""" }.plus("\n")
    } else ""

    val schema = """
    syntax = "proto3";
${imports.prependIndent()}
    option java_package = "$packageName";


    """.trimIndent()

    writer(schema)
}
