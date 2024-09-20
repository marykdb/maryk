package maryk.generator.kotlin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.exceptions.TypeException
import maryk.core.models.IsDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.values.ValuesImpl

@Suppress("UNCHECKED_CAST")
internal fun generateKotlinValue(
    definition: IsPropertyDefinition<out Any>,
    value: Any,
    addImport: (String) -> Unit,
    addGenerics: Boolean = false
): String = when (value) {
    is String -> """"$value""""
    is TimePrecision -> {
        addImport("maryk.core.properties.types.TimePrecision")
        "TimePrecision.${value.name}"
    }
    is NumberType -> {
        value.name
    }
    is IndexedEnum -> {
        val enumDefinition = definition as EnumDefinition<*>
        "${enumDefinition.enum.name}.${value.name}"
    }
    is UByte -> {
        "${value.toInt()}.toUByte()"
    }
    is UShort -> {
        "${value.toInt()}.toUShort()"
    }
    is UInt -> {
        "${value.toLong()}u"
    }
    is ULong -> {
        "${value.toLong()}uL"
    }
    is LocalTime -> {
        when {
            value.nanosecond != 0 -> "LocalTime(${value.hour}, ${value.minute}, ${value.second}, ${value.nanosecond})"
            value.second != 0 -> "LocalTime(${value.hour}, ${value.minute}, ${value.second})"
            else -> "LocalTime(${value.hour}, ${value.minute})"
        }
    }
    is LocalDateTime -> {
        when {
            value.nanosecond != 0 -> "LocalDate(${value.year}, ${value.monthNumber}, ${value.dayOfMonth}, ${value.hour}, ${value.minute}, ${value.second}, ${value.nanosecond})"
            value.second != 0 -> "LocalDateTime(${value.year}, ${value.monthNumber}, ${value.dayOfMonth}, ${value.hour}, ${value.minute}, ${value.second})"
            value.minute != 0 -> "LocalDateTime(${value.year}, ${value.monthNumber}, ${value.dayOfMonth}, ${value.hour}, ${value.minute})"
            value.hour != 0 -> "LocalDateTime(${value.year}, ${value.monthNumber}, ${value.dayOfMonth}, ${value.hour})"
            else -> "LocalDateTime(${value.year}, ${value.monthNumber}, ${value.dayOfMonth})"
        }
    }
    is LocalDate -> "LocalDate(${value.year}, ${value.monthNumber}, ${value.dayOfMonth})"
    is IsIndexedEnumDefinition<*> -> value.name
    is ValueDataModelDefinition -> value.name
    is Key<*> -> """Key("$value")"""
    is Bytes -> {
        addImport("maryk.core.properties.types.Bytes")
        """Bytes("$value")"""
    }
    is IsTransportablePropertyDefinitionType<*> -> {
        val kotlinDescriptor = value.getKotlinDescriptor()

        addImport("maryk.core.properties.definitions.${kotlinDescriptor.className}")
        for (import in kotlinDescriptor.getImports(value)) {
            addImport(import)
        }

        kotlinDescriptor.definitionToKotlin(value, addImport).trimStart()
    }
    is Set<*> -> {
        val setValues = value as Set<Any>
        val kotlinStringValues = mutableSetOf<String>()

        for (v in setValues) {
            kotlinStringValues.add(
                generateKotlinValue(definition, v, addImport)
            )
        }

        "setOf(${kotlinStringValues.joinToString(", ")})"
    }
    is List<*> -> {
        val listValues = value as List<Any>
        val kotlinStringValues = mutableListOf<String>()

        for (v in listValues) {
            kotlinStringValues.add(
                generateKotlinValue(definition, v, addImport)
            )
        }

        "listOf(${kotlinStringValues.joinToString(", ")})"
    }
    is Map<*, *> -> {
        val mapValues = value as Map<Any, Any>
        val kotlinStringValues = mutableListOf<String>()
        val mapDefinition = definition as IsMapDefinition<Any, Any, *>

        for (v in mapValues) {
            kotlinStringValues.add(
                "${generateKotlinValue(mapDefinition.keyDefinition, v.key, addImport)} to ${generateKotlinValue(
                    mapDefinition.valueDefinition,
                    v.value,
                    addImport
                )}"
            )
        }

        // Add types for enum since they are difficult sealed classes
        val type = if (addGenerics && (mapDefinition.keyDefinition is EnumDefinition<*> || mapDefinition.valueDefinition is EnumDefinition<*>)) {
            val keyType = (mapDefinition.keyDefinition as IsTransportablePropertyDefinitionType<*>).let {
                it.getKotlinDescriptor().kotlinTypeName(it)
            }
            val valueType = (mapDefinition.valueDefinition as IsTransportablePropertyDefinitionType<*>).let {
                it.getKotlinDescriptor().kotlinTypeName(it)
            }
            "<$keyType, $valueType>"
        } else ""

        "mapOf$type(${kotlinStringValues.joinToString(", ")})"
    }
    is TypedValue<*, *> -> {
        addImport("maryk.core.properties.types.TypedValue")

        val multiTypeDefinition = definition as MultiTypeDefinition<MultiTypeEnum<*>, *>
        val valueDefinition = (value.type as MultiTypeEnum<Any>).definition

        val valueAsString = generateKotlinValue(valueDefinition as IsPropertyDefinition<Any>, value.value, addImport)
        "TypedValue(${multiTypeDefinition.typeEnum.name}.${value.type.name}, $valueAsString)"
    }
    is NumberDescriptor<*> -> {
        value.type.name
    }
    is GeoPoint -> {
        "GeoPoint(${value.latitude}, ${value.longitude})"
    }
    else -> {
        when (definition) {
            is ContextualModelReferenceDefinition<*, *, *> -> {
                when (val model =
                    (value as? IsValueDataModel<*, *>) ?:
                    (value as? () -> Any)?.invoke()?.let {
                        // Fix for JS comparison issue
                        if (it is DataModelReference<*>)  {
                            it.get.invoke()
                        } else it
                    } ?:
                    (value as? () -> DataModelReference<IsDataModel>)?.invoke()?.get?.invoke()
                ) {
                    is IsValuesDataModel ->
                        """{ ${model.Meta.name} }"""
                    is IsValueDataModel<*, *> ->
                        model.Meta.name
                    else ->
                        throw TypeException("NamedDataModel $model has to be a function which returns a DataModelReference or IsValuesDataModel or IsValueDataModel")
                }
            }
            is EmbeddedValuesDefinition<*> ->
                definition.dataModel.generateKotlinValue(value as ValuesImpl, addImport)
            is ValueObjectDefinition<*, *> -> definition.dataModel.let {
                return it.generateKotlinValue(value, addImport)
            }
            else -> "$value"
        }
    }
}

private fun IsValueDataModel<*, *>.generateKotlinValue(value: Any, addImport: (String) -> Unit): String {
    val values = mutableListOf<String>()

    for (property in this) {
        @Suppress("UNCHECKED_CAST")
        val wrapper = property as AnyDefinitionWrapper
        property.getter(value)?.let {
            values.add("${property.name} = ${generateKotlinValue(wrapper.definition, it, addImport)}")
        }
    }

    return if (values.isEmpty()) {
        "${this.Meta.name}()"
    } else {
        "${this.Meta.name}(\n${values.joinToString(",\n").prependIndent()}\n)"
    }
}

private fun IsValuesDataModel.generateKotlinValue(value: ValuesImpl, addImport: (String) -> Unit): String {
    val values = mutableListOf<String>()

    for (property in this) {
        value.original(property.index)?.let {
            values.add("${property.name} = ${generateKotlinValue(property.definition, it, addImport)}")
        }
    }

    return if (values.isEmpty()) {
        "${this.Meta.name}()"
    } else {
        "${this.Meta.name}(\n${values.joinToString(",\n").prependIndent()}\n)"
    }
}
