package maryk.generator.proto3

import maryk.core.properties.enum.IsIndexedEnumDefinition

/** Generates ProtoBuf schema string for IndexedEnumDefinition */
fun IsIndexedEnumDefinition<*>.generateProto3Schema(writer: (String) -> Unit) {
    val enumName = this.name.requireProto3Identifier()
    val values = mutableListOf<String>()
    for (value in this.cases()) {
        val valueName = value.name.requireProto3Identifier()
        require(value.index > 0u) {
            "Proto3 enum index must be greater than zero: ${value.index}"
        }
        require(value.index <= Int.MAX_VALUE.toUInt()) {
            "Proto3 enum index must fit in Int32: ${value.index}"
        }
        values.add("$valueName = ${value.index};")
    }

    val schema = """
    enum $enumName {
      UNKNOWN_${enumName.uppercase()} = 0;
      ${values.joinToString("\n").prependIndent().prependIndent("  ").trimStart()}
    }
    """.trimIndent()

    writer(schema)
}
