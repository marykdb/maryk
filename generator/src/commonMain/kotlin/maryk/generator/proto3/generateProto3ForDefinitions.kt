package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.generator.kotlin.GenerationContext

fun Definitions.generateProto3(
    writerConstructor: (String) -> ((String) -> Unit)
) {
    val kotlinGenerationContext = GenerationContext()

    for (obj in this.definitions) {
        @Suppress("UNCHECKED_CAST")
        when (obj) {
            is IndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                (obj as IndexedEnumDefinition<IndexedEnum<Any>>).generateProto3Schema(writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is ValueDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as ValueDataModel<*, ObjectPropertyDefinitions<*>>).generateProto3Schema(
                    kotlinGenerationContext,
                    writer
                )
            }
            is RootDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as RootDataModel<*, PropertyDefinitions>).generateProto3Schema(kotlinGenerationContext, writer)
            }
            is DataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as DataModel<*, PropertyDefinitions>).generateProto3Schema(kotlinGenerationContext, writer)
            }
            else -> throw Exception("Unknown Maryk Primitive $obj")
        }
    }
}
