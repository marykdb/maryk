package maryk.generator.proto3

import maryk.core.models.IsStorableDataModel
import maryk.core.models.definitions.IsValuesDataModelDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.generator.kotlin.GenerationContext

internal fun IsStorableDataModel<*>.validateProto3GeneratedNames(
    modelName: String,
    generationContext: GenerationContext,
) {
    val properties = toList()
    val meta = Meta
    if (meta is IsValuesDataModelDefinition) {
        val reservedIndices = mutableSetOf<UInt>()
        meta.reservedIndices.orEmpty().forEach { reservedIndex ->
            reservedIndex.requireProto3FieldNumber()
            require(reservedIndices.add(reservedIndex)) {
                "Proto3 model $modelName contains duplicate reserved field number $reservedIndex"
            }
            properties.firstOrNull { it.index == reservedIndex }?.let { property ->
                throw IllegalArgumentException(
                    "Proto3 field ${property.name} uses reserved number $reservedIndex in model $modelName",
                )
            }
        }
        val reservedNames = mutableSetOf<String>()
        meta.reservedNames.orEmpty().forEach { reservedName ->
            require(reservedNames.add(reservedName)) {
                "Proto3 model $modelName contains duplicate reserved field name $reservedName"
            }
            properties.firstOrNull { it.name == reservedName }?.let { property ->
                throw IllegalArgumentException(
                    "Proto3 field ${property.name} uses reserved name $reservedName in model $modelName",
                )
            }
        }
    }

    val nestedSymbols = mutableMapOf<String, Proto3NestedSymbol>()
    for (property in properties) {
        property.name.requireProto3Identifier()
        property.index.requireProto3FieldNumber()
        val symbols = mutableListOf<Proto3NestedSymbol>()
        property.definition.collectNestedProto3Symbols(property.name, generationContext, symbols)
        for (symbol in symbols) {
            val existing = nestedSymbols[symbol.name]
            if (existing == null) {
                nestedSymbols[symbol.name] = symbol
            } else if (!existing.isEquivalentTo(symbol)) {
                throw IllegalArgumentException(
                    "Proto3 properties ${existing.propertyName} and ${symbol.propertyName} both generate " +
                        "nested symbol ${symbol.name} in model $modelName",
                )
            }
        }
    }

    for (property in properties) {
        nestedSymbols[property.name]?.let { symbol ->
            throw IllegalArgumentException(
                "Proto3 field ${property.name} collides with nested symbol ${symbol.name} generated for " +
                    "property ${symbol.propertyName} in model $modelName",
            )
        }
    }
}

private data class Proto3NestedSymbol(
    val name: String,
    val propertyName: String,
    val kind: String,
    val definition: Any,
) {
    fun isEquivalentTo(other: Proto3NestedSymbol): Boolean =
        kind.startsWith("enum") && other.kind == kind && definition == other.definition
}

private fun IsSerializablePropertyDefinition<*, *>.collectNestedProto3Symbols(
    propertyName: String,
    generationContext: GenerationContext,
    output: MutableList<Proto3NestedSymbol>,
) {
    when (this) {
        is EnumDefinition<*> -> if (!generationContext.enums.contains(enum)) {
            val enumName = enum.name.requireProto3Identifier()
            output += Proto3NestedSymbol(
                name = enumName,
                propertyName = propertyName,
                kind = "enum",
                definition = enum,
            )
            output += Proto3NestedSymbol(
                name = "UNKNOWN_${enumName.uppercase()}",
                propertyName = propertyName,
                kind = "enum value",
                definition = enum,
            )
            enum.cases().forEach { case ->
                output += Proto3NestedSymbol(
                    name = case.name.requireProto3Identifier(),
                    propertyName = propertyName,
                    kind = "enum value",
                    definition = enum,
                )
            }
        }
        is MultiTypeDefinition<*, *> -> {
            val helperName = "${propertyName.proto3Titlecase()}Type".requireProto3Identifier()
            output += Proto3NestedSymbol(helperName, propertyName, "multi type", this)

            val transformedFields = mutableMapOf<String, String>()
            for (typeCase in typeEnum.cases()) {
                val caseName = typeCase.name.requireProto3Identifier()
                typeCase.index.requireProto3FieldNumber()
                val transformedName = caseName.replaceFirstChar { it.lowercase() }
                transformedFields.put(transformedName, caseName)?.let { firstCase ->
                    throw IllegalArgumentException(
                        "Proto3 multi type ${typeEnum.name} cases $firstCase and $caseName both generate field $transformedName",
                    )
                }
                typeCase.definition?.collectNestedProto3Symbols(caseName, generationContext, output)
            }
        }
        is IsMapDefinition<*, *, *> -> {
            keyDefinition.collectNestedProto3Symbols(propertyName, generationContext, output)
            valueDefinition.collectNestedProto3Symbols(propertyName, generationContext, output)
            if (requiresEmbeddedProto3MapEntry()) {
                val helperName = "${propertyName.proto3Titlecase()}Entry".requireProto3Identifier()
                output += Proto3NestedSymbol(helperName, propertyName, "map entry", this)
            }
        }
        is ListDefinition<*, *> -> valueDefinition.collectNestedProto3Symbols(propertyName, generationContext, output)
        is SetDefinition<*, *> -> valueDefinition.collectNestedProto3Symbols(propertyName, generationContext, output)
    }
}

private fun IsMapDefinition<*, *, *>.requiresEmbeddedProto3MapEntry(): Boolean =
    when (val key = keyDefinition) {
        is EnumDefinition<*>,
        is FlexBytesDefinition,
        is FixedBytesDefinition,
        is ReferenceDefinition<*> -> true
        is NumberDefinition<*> -> key.type is Float32 || key.type is Float64
        else -> false
    } || valueDefinition is IsMapDefinition<*, *, *> ||
        valueDefinition is IsCollectionDefinition<*, *, *, *>

private fun String.proto3Titlecase(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
