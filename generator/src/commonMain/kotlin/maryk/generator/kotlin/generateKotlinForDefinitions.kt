package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.core.exceptions.TypeException
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.properties.IsModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValueModel
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition

fun Definitions.generateKotlin(
    packageName: String,
    writerConstructor: (String) -> ((String) -> Unit)
) {
    val kotlinGenerationContext = GenerationContext()

    for (obj in this.definitions) {
        when (obj) {
            is IndexedEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                obj.generateKotlin(packageName, writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is MultiTypeEnumDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                obj.generateKotlin(packageName, writer)
                kotlinGenerationContext.enums.add(obj)
            }
            is ValueDataModelDefinition<*, *> -> {
                val writer = writerConstructor(obj.name)
                (obj.properties as IsValueModel<*, *>).generateKotlin(
                    packageName,
                    kotlinGenerationContext,
                    writer
                )
            }
            is RootDataModelDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                (obj.properties as IsRootModel).generateKotlin(
                    packageName,
                    kotlinGenerationContext,
                    writer
                )
            }
            is DataModelDefinition<*> -> {
                val writer = writerConstructor(obj.name)
                (obj.properties as IsModel).generateKotlin(packageName, kotlinGenerationContext, writer)
            }
            else -> throw TypeException("Unknown Maryk Primitive $obj")
        }
    }
}
