package maryk.generator.proto3

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberType
import maryk.generator.kotlin.GenerationContext

fun <P: AbstractPropertyDefinitions<*>> IsNamedDataModel<P>.generateProto3Schema(
    generationContext: GenerationContext,
    writer: (String) -> Unit
) {
    val subMessages = mutableListOf<String>()

    val messageAdder: (String) -> Unit = {
        if (!subMessages.contains(it)) {
            subMessages.add(it)
        }
    }

    val properties = this.properties.generateSchemaForProperties(generationContext, messageAdder)

    val precedingMessages = if (subMessages.isNotEmpty()) {
        subMessages.joinToString("\n").plus("\n")
            .prependIndent().prependIndent("  ").trimStart().plus("  ")
    } else ""

    val schema = """
    message $name {
      $precedingMessages${properties.prependIndent().prependIndent("  ").trimStart()}
    }
    """.trimIndent()

    writer(schema)
}

private fun AbstractPropertyDefinitions<*>.generateSchemaForProperties(
    generationContext: GenerationContext,
    messageAdder: (String) -> Unit
): String {
    var properties = ""

    for (it in this) {
        val type = it.definition.toProtoBufType(it.name, generationContext, messageAdder)
        properties += "$type ${it.name} = ${it.index};\n"
    }
    return properties.trimEnd()
}

private fun IsSerializablePropertyDefinition<*, *>.toProtoBufType(
    name: String,
    generationContext: GenerationContext,
    messageAdder: (String) -> Unit
): String {
    return when(this) {
        is StringDefinition -> "string"
        is BooleanDefinition -> "bool"
        is FixedBytesDefinition,
        is FlexBytesDefinition,
        is ValueModelDefinition<*, *, *>,
        is ReferenceDefinition<*> -> "bytes"
        is TimeDefinition -> "uint32"
        is DateDefinition -> "sint32"
        is DateTimeDefinition -> "int64"
        is NumberDefinition<*> -> when(this.type.type) {
            NumberType.SInt8,
            NumberType.SInt16,
            NumberType.SInt32 -> "sint32"
            NumberType.SInt64 -> "sint64"
            NumberType.UInt8,
            NumberType.UInt16,
            NumberType.UInt32 -> "uint64"
            NumberType.UInt64 -> "uint64"
            NumberType.Float32 -> "float"
            NumberType.Float64 -> "double"
        }
        is EnumDefinition<*> -> {
            if (!generationContext.enums.contains(this.enum)) {
                var enumSchema = ""
                this.enum.generateProto3Schema {
                    enumSchema += it
                }

                messageAdder(enumSchema)
            }
            this.enum.name
        }
        is SetDefinition<*, *> -> "repeated ${this.valueDefinition.toProtoBufType(name, generationContext, messageAdder)}"
        is ListDefinition<*, *> -> "repeated ${this.valueDefinition.toProtoBufType(name, generationContext, messageAdder)}"
        is MapDefinition<*, *, *> -> {
            val keyDefinition = this.keyDefinition
            when(keyDefinition) {
                is EnumDefinition<*>,
                is FlexBytesDefinition,
                is FixedBytesDefinition,
                is ReferenceDefinition<*> -> return createEmbeddedMapModel(name, keyDefinition, valueDefinition, generationContext, messageAdder)
                is NumberDefinition<*> -> when (keyDefinition.type) {
                    is Float32,
                    is Float64 -> return createEmbeddedMapModel(name, keyDefinition, valueDefinition, generationContext, messageAdder)
                }
                else -> {
                    //continue
                }
            }

            // Separate object
            "map<${this.keyDefinition.toProtoBufType(name, generationContext, messageAdder)}, ${this.valueDefinition.toProtoBufType(name, generationContext, messageAdder)}>"
        }
        is EmbeddedValuesDefinition<*, *> -> this.dataModel.name
        is EmbeddedObjectDefinition<*, *, *, *, *> -> (this.dataModel as IsNamedDataModel<*>).name
        is MultiTypeDefinition<*, *> -> {
            val multiTypeName = "${name.capitalize()}Type"

            val multiTypes = mutableListOf<String>()
            for (it in this.definitionMap.keys) {
                val type = this.definitionMap[it]!!.toProtoBufType(it.name, generationContext, messageAdder)
                multiTypes += "$type ${it.name.decapitalize()} = ${it.index};"
            }
            messageAdder("""
            message $multiTypeName {
              oneof $name {
                ${multiTypes.joinToString(separator = "\n    ").prependIndent().prependIndent().prependIndent().trimStart()}
              }
            }
            """.trimIndent())

            multiTypeName
        }
        else -> throw Exception("Unknown type $this")
    }
}

private fun createEmbeddedMapModel(
    name: String,
    keyDefinition: IsSimpleValueDefinition<*, *>,
    valueDefinition: IsSubDefinition<out Any, Nothing>,
    generationContext: GenerationContext,
    messageAdder: (String) -> Unit
): String {
    val keyType = keyDefinition.toProtoBufType(name, generationContext, messageAdder)
    val valueType = valueDefinition.toProtoBufType(name, generationContext, messageAdder)
    val entryObjectName = "${name.capitalize()}Entry"
    messageAdder("""
    message $entryObjectName {
      $keyType key = 1;
      $valueType value = 2;
    }
    """.trimIndent())
    return "repeated $entryObjectName"
}
