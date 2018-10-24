package maryk.generator.kotlin

import maryk.core.models.DataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.models.ValueDataModel
import maryk.core.objects.ValuesImpl
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Date
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt8
import maryk.lib.time.DateTime
import maryk.lib.time.Time

@Suppress("UNCHECKED_CAST", "EXPERIMENTAL_API_USAGE")
internal fun generateKotlinValue(definition: IsPropertyDefinition<Any>, value: Any, addImport: (String) -> Unit): String = when(value) {
    is String -> """"$value""""
    is TimePrecision -> {
        addImport("maryk.core.properties.types.TimePrecision")
        "TimePrecision.${value.name}"
    }
    is NumberType -> {
        value.name
    }
    is Enum<*> -> {
        val enumDefinition = definition as EnumDefinition<*>
        "${enumDefinition.enum.name}.${value.name}"
    }
    is UInt8 -> {
        addImport("maryk.core.properties.types.numeric.toUInt8")
        "${value.toInt()}.toUInt8()"
    }
    is UInt16 -> {
        addImport("maryk.core.properties.types.numeric.toUInt16")
        "${value.toInt()}.toUInt16()"
    }
    is UInt -> {
        "${value.toLong()}u"
    }
    is ULong -> {
        "${value.toLong()}uL"
    }
    is Time -> {
        when {
            value.milli != 0.toShort() -> "Time(${value.hour}, ${value.minute}, ${value.second}, ${value.milli})"
            value.second != 0.toByte() -> "Time(${value.hour}, ${value.minute}, ${value.second})"
            else -> "Time(${value.hour}, ${value.minute})"
        }
    }
    is DateTime -> {
        when {
            value.milli != 0.toShort() -> "DateTime(${value.year}, ${value.month}, ${value.day}, ${value.hour}, ${value.minute}, ${value.second}, ${value.milli})"
            value.second != 0.toByte() -> "DateTime(${value.year}, ${value.month}, ${value.day}, ${value.hour}, ${value.minute}, ${value.second})"
            value.minute != 0.toByte() -> "DateTime(${value.year}, ${value.month}, ${value.day}, ${value.hour}, ${value.minute})"
            value.hour != 0.toByte() -> "DateTime(${value.year}, ${value.month}, ${value.day}, ${value.hour})"
            else -> "DateTime(${value.year}, ${value.month}, ${value.day})"
        }
    }
    is Date ->  "Date(${value.year}, ${value.month}, ${value.day})"
    is IndexedEnumDefinition<*> -> value.name
    is ValueDataModel<*, *> -> {
        value.name
    }
    is Key<*> -> """Key("$value")"""
    is Bytes -> {
        addImport("maryk.core.properties.types.Bytes")
        """Bytes("$value")"""
    }
    is IsTransportablePropertyDefinitionType<*> -> {
        @Suppress("UNCHECKED_CAST")
        val kotlinDescriptor =
            (value as IsTransportablePropertyDefinitionType<Any>).getKotlinDescriptor()

        for (import in kotlinDescriptor.getImports(value)) {
            addImport(import)
        }

        kotlinDescriptor.definitionToKotlin(value, addImport).trimStart()
    }
    is Set<*> -> {
        @Suppress("UNCHECKED_CAST")
        val setValues = value as Set<Any>
        val kotlinStringValues = mutableSetOf<String>()

        for (v in setValues) {
            @Suppress("UNCHECKED_CAST")
            kotlinStringValues.add(
                generateKotlinValue(definition, v, addImport)
            )
        }

        "setOf(${kotlinStringValues.joinToString(", ")})"
    }
    is List<*> -> {
        @Suppress("UNCHECKED_CAST")
        val listValues = value as List<Any>
        val kotlinStringValues = mutableListOf<String>()

        for (v in listValues) {
            @Suppress("UNCHECKED_CAST")
            kotlinStringValues.add(
                generateKotlinValue(definition, v, addImport)
            )
        }

        "listOf(${kotlinStringValues.joinToString(", ")})"
    }
    is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        val mapValues = value as Map<Any, Any>
        val kotlinStringValues = mutableListOf<String>()
        val mapDefinition = definition as IsMapDefinition<Any, Any, *>

        for (v in mapValues) {
            @Suppress("UNCHECKED_CAST")
            kotlinStringValues.add(
                "${generateKotlinValue(mapDefinition.keyDefinition, v.key, addImport)} to ${generateKotlinValue(mapDefinition.valueDefinition, v.value, addImport)}"
            )
        }

        "mapOf(${kotlinStringValues.joinToString(", ")})"
    }
    is TypedValue<*, *> -> {
        addImport("maryk.core.properties.types.TypedValue")

        val multiTypeDefinition = definition as MultiTypeDefinition<*, *>
        val valueDefinition = multiTypeDefinition.definitionMap[value.type]

        @Suppress("UNCHECKED_CAST")
        val valueAsString = generateKotlinValue(valueDefinition as IsPropertyDefinition<Any>, value.value, addImport)
        "TypedValue(${multiTypeDefinition.typeEnum.name}.${value.type.name}, $valueAsString)"
    }
    is NumberDescriptor<*> -> {
        value.type.name
    }
    else -> {
        when (definition) {
            is ContextualModelReferenceDefinition<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                (value as? () -> IsNamedDataModel<*>)?.let {
                    """{ ${value().name} }"""
                } ?: throw Exception("NamedDataModel $value cannot be null")
            }
            is EmbeddedValuesDefinition<*, *> -> (definition.dataModel as? DataModel<*, *>)?.let {
                @Suppress("UNCHECKED_CAST")
                return it.generateKotlinValue(value as ValuesImpl, addImport)
            } ?: throw Exception("DataModel ${definition.dataModel} cannot be used to generate Kotlin code")
            is ValueModelDefinition<*, *, *> -> definition.dataModel.let {
                return it.generateKotlinValue(value, addImport)
            }
            else -> "$value"
        }
    }
}

private fun ObjectDataModel<*, *>.generateKotlinValue(value: Any, addImport: (String) -> Unit): String {
    val values = mutableListOf<String>()

    for(property in this.properties) {
        @Suppress("UNCHECKED_CAST")
        val wrapper = property as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>
        property.getter(value)?.let {
            values.add("${property.name} = ${generateKotlinValue(wrapper.definition, it, addImport)}")
        }
    }

    return if (values.isEmpty()) {
        "${this.name}()"
    } else {
        "${this.name}(\n${values.joinToString(",\n").prependIndent()}\n)"
    }

}

private fun DataModel<*, *>.generateKotlinValue(value: ValuesImpl, addImport: (String) -> Unit): String {
    val values = mutableListOf<String>()

    for(property in this.properties) {
        val wrapper = property

        value.original(property.index)?.let {
            values.add("${property.name} = ${generateKotlinValue(wrapper.definition, it, addImport)}")
        }
    }

    return if (values.isEmpty()) {
        "${this.name}()"
    } else {
        "${this.name}(\n${values.joinToString(",\n").prependIndent()}\n)"
    }

}
