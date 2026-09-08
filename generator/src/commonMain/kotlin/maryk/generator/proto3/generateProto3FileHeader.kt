package maryk.generator.proto3

fun generateProto3FileHeader(
    packageName: String,
    protosToImport: List<String> = listOf(),
    writer: (String) -> Unit
) {
    packageName.requireProto3PackageName()
    protosToImport.forEach { it.requireProto3ImportName() }

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

private fun String.requireProto3PackageName() {
    require(isNotBlank() && split('.').all { it.matches(proto3Identifier) }) {
        "Proto3 package name is invalid: $this"
    }
}

private fun String.requireProto3ImportName() {
    require(matches(proto3Identifier)) { "Proto3 import name is invalid: $this" }
}
