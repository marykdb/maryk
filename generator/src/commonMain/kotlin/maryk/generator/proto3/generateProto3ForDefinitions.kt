package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.core.exceptions.TypeException
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.definitions.ValueDataModelDefinition
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
            is ValueDataModelDefinition<*, *> -> {
                val writer = writerConstructor(obj.name)
                obj.generateProto3Schema(
                    kotlinGenerationContext,
                    writer
                )
            }
            is RootDataModelDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                obj.generateProto3Schema(kotlinGenerationContext, writer)
            }
            is DataModelDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                obj.generateProto3Schema(kotlinGenerationContext, writer)
            }
            else -> throw TypeException("Unknown Maryk Primitive $obj")
        }
    }
}
