package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.core.exceptions.TypeException
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition

fun Definitions.generateKotlin(
    packageName: String,
    writerConstructor: (String) -> ((String) -> Unit)
) {
    val outputNames = definitions.map { it.Meta.name }
    outputNames.groupBy { it }
        .entries
        .firstOrNull { it.value.size > 1 }
        ?.let { (name) ->
            throw IllegalArgumentException("Kotlin definitions generate duplicate output name $name")
        }
    outputNames.groupBy { it.lowercase() }
        .values
        .firstOrNull { it.distinct().size > 1 }
        ?.let { names ->
            throw IllegalArgumentException(
                "Kotlin definitions output names ${names.distinct().joinToString()} collide after case normalization",
            )
        }

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
            is IsValueDataModel<*, *> -> {
                val writer = writerConstructor(obj.Meta.name)
                obj.generateKotlin(
                    packageName,
                    kotlinGenerationContext,
                    writer
                )
            }
            is IsRootDataModel -> {
                val writer = writerConstructor(obj.Meta.name)
                obj.generateKotlin(
                    packageName,
                    kotlinGenerationContext,
                    writer
                )
            }
            is DataModel<*> -> {
                val writer = writerConstructor(obj.Meta.name)
                obj.generateKotlin(packageName, kotlinGenerationContext, writer)
            }
            else -> throw TypeException("Unknown Maryk Primitive $obj")
        }
    }
}
