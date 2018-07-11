package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.core.models.ObjectDataModel
import maryk.core.models.RootObjectDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.ValueDataObject

fun Definitions.generateKotlin(packageName: String, writerConstructor: (String) -> ((String) -> Unit)) {
    val kotlinGenerationContext = KotlinGenerationContext()

    for (obj in this.definitions) {
        @Suppress("UNCHECKED_CAST")
        when (obj) {
            is IndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                (obj as IndexedEnumDefinition<IndexedEnum<Any>>).generateKotlin(packageName, writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is RootObjectDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>).generateKotlin(packageName, kotlinGenerationContext, writer)
            }
            is ValueDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>).generateKotlin(packageName, kotlinGenerationContext, writer)
            }
            is ObjectDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as ObjectDataModel<Any, ObjectPropertyDefinitions<Any>>).generateKotlin(packageName, kotlinGenerationContext, writer)
            }
            else -> throw Exception("Unknown Maryk Primitive $obj")
        }
    }
}
