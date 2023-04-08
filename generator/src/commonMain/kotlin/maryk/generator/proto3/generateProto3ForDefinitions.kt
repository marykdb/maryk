package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.core.exceptions.TypeException
import maryk.core.models.IsStorableDataModel
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.generator.kotlin.GenerationContext

fun Definitions.generateProto3(
    writerConstructor: (String) -> ((String) -> Unit)
) {
    val kotlinGenerationContext = GenerationContext()

    for (obj in this.definitions) {
        when (obj) {
            is IsIndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                obj.generateProto3Schema(writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is IsStorableDataModel<*> -> {
                val writer = writerConstructor(obj.Meta.name)
                obj.generateProto3Schema(kotlinGenerationContext, writer)
            }
            else -> throw TypeException("Unknown Maryk Primitive $obj")
        }
    }
}
