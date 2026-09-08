package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.core.exceptions.TypeException
import maryk.core.models.IsStorableDataModel
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.generator.kotlin.GenerationContext

fun Definitions.generateProto3(
    writerConstructor: (String) -> ((String) -> Unit)
) {
    val outputNames = definitions.map { definition ->
        when (definition) {
            is IsIndexedEnumDefinition<*> -> definition.name.requireProto3Identifier()
            is IsStorableDataModel<*> -> definition.Meta.name.requireProto3Identifier()
            else -> throw TypeException("Unknown Maryk Primitive $definition")
        }
    }
    outputNames.groupBy { it }
        .entries
        .firstOrNull { it.value.size > 1 }
        ?.let { (name) ->
            throw IllegalArgumentException("Proto3 definitions generate duplicate output name $name")
        }

    val kotlinGenerationContext = GenerationContext()

    for (obj in this.definitions) {
        when (obj) {
            is IsIndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name.requireProto3Identifier())
                obj.generateProto3Schema(writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is IsStorableDataModel<*> -> {
                val writer = writerConstructor(obj.Meta.name.requireProto3Identifier())
                obj.generateProto3Schema(kotlinGenerationContext, writer)
            }
            else -> throw TypeException("Unknown Maryk Primitive $obj")
        }
    }
}
