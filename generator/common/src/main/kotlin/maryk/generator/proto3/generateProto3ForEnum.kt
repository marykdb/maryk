package maryk.generator.proto3

import maryk.core.properties.enum.IndexedEnumDefinition

fun IndexedEnumDefinition<*>.generateProto3SchemaFile(packageName: String, writer: (String) -> Unit) {
    writer("syntax = \"proto3\";\n\noption java_package = \"$packageName\";\n\n")
    this.generateProto3Schema(writer)
}

/** Generates protobuf schema string for IndexedEnumDefinition */
fun IndexedEnumDefinition<*>.generateProto3Schema(writer: (String) -> Unit) {
    val values = mutableListOf<String>()
    for (value in this.values()) {
        values.add("${value.name} = ${value.index};")
    }

    val schema = """
    enum ${this.name} {
      UNKNOWN = 0;
      ${values.joinToString("\n").prependIndent().prependIndent("  ").trimStart()}
    }
    """.trimIndent()

    writer(schema)
}
