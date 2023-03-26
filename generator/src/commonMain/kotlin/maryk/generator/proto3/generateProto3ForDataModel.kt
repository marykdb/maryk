package maryk.generator.proto3

import maryk.core.exceptions.TypeException
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsTypedPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberType.Float32Type
import maryk.core.properties.types.numeric.NumberType.Float64Type
import maryk.core.properties.types.numeric.NumberType.SInt16Type
import maryk.core.properties.types.numeric.NumberType.SInt32Type
import maryk.core.properties.types.numeric.NumberType.SInt64Type
import maryk.core.properties.types.numeric.NumberType.SInt8Type
import maryk.core.properties.types.numeric.NumberType.UInt16Type
import maryk.core.properties.types.numeric.NumberType.UInt32Type
import maryk.core.properties.types.numeric.NumberType.UInt64Type
import maryk.core.properties.types.numeric.NumberType.UInt8Type
import maryk.generator.kotlin.GenerationContext

fun <P : IsTypedPropertyDefinitions<*>> IsNamedDataModel<P>.generateProto3Schema(
    generationContext: GenerationContext,
    writer: (String) -> Unit
) {
    val subMessages = mutableListOf<String>()

    val messageAdder: (String) -> Unit = {
        if (!subMessages.contains(it)) {
            subMessages.add(it)
        }
    }

    var reservations = ""

    if (this is IsValuesDataModel<*>) {
        this.reservedIndices?.let { indices ->
            reservations += "reserved ${indices.joinToString(", ")};\n      "
        }
        this.reservedNames?.let { names ->
            reservations += "reserved ${names.joinToString{ "\"$it\"" }};\n      "
        }
        if (reservations.isNotBlank()) reservations.prependIndent().prependIndent("  ")
    }

    val properties = this.properties.generateSchemaForProperties(generationContext, messageAdder)

    val precedingMessages = if (subMessages.isNotEmpty()) {
        subMessages.joinToString("\n").plus("\n")
            .prependIndent().prependIndent("  ").trimStart().plus("  ")
    } else ""

    val schema = """
    message $name {
      $reservations$precedingMessages${properties.prependIndent().prependIndent("  ").trimStart()}
    }
    """.trimIndent()

    writer(schema)
}

private fun IsTypedPropertyDefinitions<*>.generateSchemaForProperties(
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
    return when (this) {
        is StringDefinition -> "string"
        is BooleanDefinition -> "bool"
        is FixedBytesDefinition,
        is FlexBytesDefinition,
        is ValueObjectDefinition<*, *>,
        is ReferenceDefinition<*> -> "bytes"
        is TimeDefinition -> "uint32"
        is DateDefinition -> "sint32"
        is DateTimeDefinition -> "int64"
        is GeoPointDefinition -> "int64"
        is NumberDefinition<*> -> when (this.type.type) {
            SInt8Type,
            SInt16Type,
            SInt32Type -> "sint32"
            SInt64Type -> "sint64"
            UInt8Type,
            UInt16Type,
            UInt32Type -> "uint64"
            UInt64Type -> "uint64"
            Float32Type -> "float"
            Float64Type -> "double"
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
        is SetDefinition<*, *> -> "repeated ${this.valueDefinition.toProtoBufType(
            name,
            generationContext,
            messageAdder
        )}"
        is ListDefinition<*, *> -> "repeated ${this.valueDefinition.toProtoBufType(
            name,
            generationContext,
            messageAdder
        )}"
        is IsMapDefinition<*, *, *> -> {
            // The following types are not supported as key so should be an embedded map model
            when (val keyDefinition = this.keyDefinition) {
                is EnumDefinition<*>,
                is FlexBytesDefinition,
                is FixedBytesDefinition,
                is ReferenceDefinition<*> -> return createEmbeddedMapModel(
                    name,
                    keyDefinition,
                    valueDefinition,
                    generationContext,
                    messageAdder
                )
                is NumberDefinition<*> -> when (keyDefinition.type) {
                    is Float32,
                    is Float64 -> return createEmbeddedMapModel(
                        name,
                        keyDefinition,
                        valueDefinition,
                        generationContext,
                        messageAdder
                    )
                }
                else -> {
                    //continue
                }
            }

            // The following types are not supported as value so should be an embedded map model
            when (val valueDefinition = this.valueDefinition) {
                is IsMapDefinition<*, *, *>,
                is IsCollectionDefinition<*, *, *, *> -> return createEmbeddedMapModel(
                    name,
                    keyDefinition,
                    valueDefinition,
                    generationContext,
                    messageAdder
                )
                else -> {
                    //continue
                }
            }

            // Separate object
            "map<${this.keyDefinition.toProtoBufType(name, generationContext, messageAdder)}, ${this.valueDefinition.toProtoBufType(name, generationContext, messageAdder)}>"
        }
        is EmbeddedValuesDefinition<*> -> this.dataModel.Model.name
        is EmbeddedObjectDefinition<*, *, *, *> -> (this.dataModel.Model as IsNamedDataModel<*>).name
        is MultiTypeDefinition<*, *> -> {
            val multiTypeName = "${name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Type"

            val multiTypes = mutableListOf<String>()
            val canBeAOneOf = this.typeEnum.cases().firstOrNull {
                it.definition is IsCollectionDefinition<*, *, *, *> || it.definition is IsMapDefinition<*, *, *>
            } == null

            for (typeCase in this.typeEnum.cases()) {
                val type = typeCase.definition!!.toProtoBufType(typeCase.name, generationContext, messageAdder)
                multiTypes += "$type ${typeCase.name.replaceFirstChar { it.lowercase() }} = ${typeCase.index};"
            }

            val properties = multiTypes.joinToString(separator = "\n    ").prependIndent().prependIndent().prependIndent()

            if (canBeAOneOf) {
                messageAdder(
                """
                message $multiTypeName {
                  oneof $name {
                    ${properties.prependIndent().trimStart()}
                  }
                }
                """.trimIndent()
                )
            } else {
                messageAdder(
                """
                // Only one of the properties can be set. Is not a `oneof` because of a repeated type or map
                message $multiTypeName {
                  ${properties.prependIndent("  ").trimStart()}
                }
                """.trimIndent()
                )
            }

            multiTypeName
        }
        else -> throw TypeException("Unknown type $this")
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
    val entryObjectName = "${name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Entry"
    messageAdder("""
    message $entryObjectName {
      $keyType key = 1;
      $valueType value = 2;
    }
    """.trimIndent())
    return "repeated $entryObjectName"
}
