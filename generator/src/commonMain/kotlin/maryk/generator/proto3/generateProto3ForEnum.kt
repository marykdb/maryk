package maryk.generator.proto3

import maryk.core.properties.enum.IsIndexedEnumDefinition

/** Generates ProtoBuf schema string for IndexedEnumDefinition */
fun IsIndexedEnumDefinition<*>.generateProto3Schema(writer: (String) -> Unit) {
    val values = mutableListOf<String>()
    for (value in this.cases()) {
        values.add("${value.name} = ${value.index};")
    }

    val schema = """
    enum ${this.name} {
      UNKNOWN_${this.name.uppercase()} = 0;
      ${values.joinToString("\n").prependIndent().prependIndent("  ").trimStart()}
    }
    """.trimIndent()

    writer(schema)
}
