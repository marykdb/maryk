package maryk.generator.proto3

import maryk.core.properties.enum.IndexedEnumDefinition

/** Generates protobuf schema string for IndexedEnumDefinition */
fun IndexedEnumDefinition<*>.generateProto3Schema(writer: (String) -> Unit) {
    val values = mutableListOf<String>()
    for (value in this.cases()) {
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
