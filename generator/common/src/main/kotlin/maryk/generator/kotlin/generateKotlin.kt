package maryk.generator.kotlin

import maryk.core.objects.DataModel
import maryk.core.definitions.MarykPrimitive
import maryk.core.objects.RootDataModel
import maryk.core.objects.ValueDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.ValueDataObject

fun generateKotlin(packageName: String, vararg objects: MarykPrimitive, writerConstructor: (String) -> ((String) -> Unit)) {
    for (obj in objects) {
        @Suppress("UNCHECKED_CAST")
        when (obj) {
            is IndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                (obj as IndexedEnumDefinition<IndexedEnum<Any>>).generateKotlin(packageName, writer)
            }
            is RootDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as RootDataModel<Any, PropertyDefinitions<Any>>).generateKotlin(packageName, writer)
            }
            is ValueDataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as ValueDataModel<ValueDataObject, PropertyDefinitions<ValueDataObject>>).generateKotlin(packageName, writer)
            }
            is DataModel<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj as DataModel<Any, PropertyDefinitions<Any>>).generateKotlin(packageName, writer)
            }
            else -> throw Exception("Unknown Maryk Primitive $obj")
        }
    }
}
