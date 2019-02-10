package maryk.generator.kotlin

import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsSimpleObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.NumberType

/** Get the PropertyDefinitionKotlinDescriptor of the given property */
internal fun <T: Any, D: IsTransportablePropertyDefinitionType<T>, P: ObjectPropertyDefinitions<D>> D.getKotlinDescriptor(): PropertyDefinitionKotlinDescriptor<T, D, P> {
    @Suppress("UNCHECKED_CAST")
    return definitionNamesMap[this.propertyDefinitionType] as PropertyDefinitionKotlinDescriptor<T, D, P>?
        ?: throw Exception("Unknown propertyDefinitionType ${this.propertyDefinitionType}")
}


private val dateImports = arrayOf("maryk.lib.time.Date")
private val dateTimeImports = arrayOf("maryk.lib.time.DateTime")
private val timeImports = arrayOf("maryk.lib.time.Time")
private val keyImports = arrayOf("maryk.core.properties.types.Key")
private val uInt8Imports = arrayOf("maryk.core.properties.types.numeric.UInt8")
private val uInt16Imports = arrayOf("maryk.core.properties.types.numeric.UInt16")
private val uInt32Imports = arrayOf("maryk.core.properties.types.numeric.UInt32")
private val uInt64Imports = arrayOf("maryk.core.properties.types.numeric.UInt64")
private val sInt8Imports = arrayOf("maryk.core.properties.types.numeric.SInt8")
private val sInt16Imports = arrayOf("maryk.core.properties.types.numeric.SInt16")
private val sInt32Imports = arrayOf("maryk.core.properties.types.numeric.SInt32")
private val sInt64Imports = arrayOf("maryk.core.properties.types.numeric.SInt64")
private val float32Imports = arrayOf("maryk.core.properties.types.numeric.Float32")
private val float64Imports = arrayOf("maryk.core.properties.types.numeric.Float64")
private val valuesImports = arrayOf("maryk.core.values.Values")

private val generateKotlinValueWithDefinition: (IsTransportablePropertyDefinitionType<Any>, Any, (String) -> Unit) -> String = { definition, value, addImport ->
    generateKotlinValue(definition, value, addImport)
}

@Suppress("UNCHECKED_CAST")
private val definitionNamesMap = mapOf(
    PropertyDefinitionType.Boolean to PropertyDefinitionKotlinDescriptor(
        className = "BooleanDefinition",
        kotlinTypeName = { "Boolean" },
        definitionModel = BooleanDefinition.Model
    ),
    PropertyDefinitionType.Date to PropertyDefinitionKotlinDescriptor(
        className = "DateDefinition",
        kotlinTypeName = { "Date" },
        definitionModel = DateDefinition.Model,
        imports = { dateImports }
    ),
    PropertyDefinitionType.DateTime to PropertyDefinitionKotlinDescriptor(
        className = "DateTimeDefinition",
        kotlinTypeName = { "DateTime" },
        definitionModel = DateTimeDefinition.Model,
        imports = { dateTimeImports }
    ),
    PropertyDefinitionType.Enum to PropertyDefinitionKotlinDescriptor(
        className = "EnumDefinition",
        kotlinTypeName = { it.enum.name },
        definitionModel = EnumDefinition.Model as IsSimpleObjectDataModel<EnumDefinition<IndexedEnum<IndexedEnum<*>>>>,
        propertyValueOverride = mapOf(
            "maxValue" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "minValue" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "default" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "values" to { _, _, _ ->
                null
            },
            "name" to { definition, _, _ ->
                val enumDefinition = definition as EnumDefinition<*>

                enumDefinition.enum.name
            }
        ),
        propertyNameOverride = mapOf(
            "name" to "enum"
        )
    ),
    PropertyDefinitionType.FixedBytes to PropertyDefinitionKotlinDescriptor(
        className = "FixedBytesDefinition",
        kotlinTypeName = { "Bytes" },
        definitionModel = FixedBytesDefinition.Model
    ),
    PropertyDefinitionType.FlexBytes to PropertyDefinitionKotlinDescriptor(
        className = "FlexBytesDefinition",
        kotlinTypeName = { "Bytes" },
        definitionModel = FlexBytesDefinition.Model
    ),
    PropertyDefinitionType.List to PropertyDefinitionKotlinDescriptor(
        className = "ListDefinition",
        kotlinTypeName = {
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<Any>
            val kotlinDescriptorForValueDefinition = transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "List<$kotlinDescriptorForValueDefinition>"
        },
        definitionModel = ListDefinition.Model as IsSimpleObjectDataModel<ListDefinition<Any, *>>
    ),
    PropertyDefinitionType.Map to PropertyDefinitionKotlinDescriptor(
        className = "MapDefinition",
        kotlinTypeName = {
            val transportableKeyDefinition = it.keyDefinition as IsTransportablePropertyDefinitionType<Any>
            val kotlinDescriptorForKeyDefinition = transportableKeyDefinition.getKotlinDescriptor().kotlinTypeName(transportableKeyDefinition)
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<Any>
            val kotlinDescriptorForValueDefinition = transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Map<$kotlinDescriptorForKeyDefinition, $kotlinDescriptorForValueDefinition>"
        },
        definitionModel = MapDefinition.Model as IsSimpleObjectDataModel<MapDefinition<Any, Any, *>>
    ),
    PropertyDefinitionType.MultiType to PropertyDefinitionKotlinDescriptor(
        className = "MultiTypeDefinition",
        kotlinTypeName = { "TypedValue<${it.typeEnum.name}, *>" },
        definitionModel = MultiTypeDefinition.Model as IsSimpleObjectDataModel<MultiTypeDefinition<IndexedEnum<Any>, *>>,
        propertyValueOverride = mapOf(
            "definitionMap" to { definition, _, _ ->
                val multiTypeDefinition = definition as MultiTypeDefinition<IndexedEnum<IndexedEnum<*>>, IsPropertyContext>

                val typeName = multiTypeDefinition.typeEnum.name

                val typeValues = mutableListOf<String>()

                for (typeDefinition in multiTypeDefinition.definitionMap) {
                    @Suppress("UNCHECKED_CAST")
                    val value = typeDefinition.value as IsTransportablePropertyDefinitionType<Any>
                    val valueDefinition = value.getKotlinDescriptor()
                    val valueAsString = valueDefinition.definitionToKotlin(value) {}.trimStart()
                    typeValues.add("$typeName.${typeDefinition.key.name} to $valueAsString")
                }

                val types = typeValues.joinToString(",\n").prependIndent()

                "definitionMap(\n$types\n)"
            },
            "default" to generateKotlinValueWithDefinition
        )
    ),
    PropertyDefinitionType.Number to PropertyDefinitionKotlinDescriptor(
        className = "NumberDefinition",
        kotlinTypeName = {
            when (it.type.type) {
                NumberType.SInt8 -> "Byte"
                NumberType.SInt16 -> "Short"
                NumberType.SInt32 -> "Int"
                NumberType.SInt64 -> "Long"
                NumberType.UInt8 -> "UByte"
                NumberType.UInt16 -> "UShort"
                NumberType.UInt32 -> "UInt"
                NumberType.UInt64 -> "ULong"
                NumberType.Float32 -> "Float"
                NumberType.Float64 -> "Double"
            }
        },
        imports = {
            when (it.type.type) {
                NumberType.UInt8 -> uInt8Imports
                NumberType.UInt16 -> uInt16Imports
                NumberType.UInt32 -> uInt32Imports
                NumberType.UInt64 -> uInt64Imports
                NumberType.SInt8 -> sInt8Imports
                NumberType.SInt16 -> sInt16Imports
                NumberType.SInt32 -> sInt32Imports
                NumberType.SInt64 -> sInt64Imports
                NumberType.Float32 -> float32Imports
                NumberType.Float64 -> float64Imports
            }
        },
        definitionModel = NumberDefinition.Model as IsSimpleObjectDataModel<NumberDefinition<Comparable<Any>>>
    ),
    PropertyDefinitionType.Reference to PropertyDefinitionKotlinDescriptor(
        className = "ReferenceDefinition",
        kotlinTypeName = { "Key<${it.dataModel.name}>" },
        imports = { keyImports },
        definitionModel = ReferenceDefinition.Model as IsSimpleObjectDataModel<ReferenceDefinition<IsRootDataModel<*>>>
    ),
    PropertyDefinitionType.Set to PropertyDefinitionKotlinDescriptor(
        className = "SetDefinition",
        kotlinTypeName = {
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<Any>
            val kotlinDescriptorForValueDefinition = transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Set<$kotlinDescriptorForValueDefinition>"
        },
        definitionModel = SetDefinition.Model as IsSimpleObjectDataModel<SetDefinition<Any, *>>
    ),
    PropertyDefinitionType.String to PropertyDefinitionKotlinDescriptor(
        className = "StringDefinition",
        kotlinTypeName = { "String" },
        definitionModel = StringDefinition.Model
    ),
    PropertyDefinitionType.Embed to PropertyDefinitionKotlinDescriptor(
        className = "EmbeddedValuesDefinition",
        kotlinTypeName = {
            val modelName = (it.dataModel as IsNamedDataModel<*>).name
            "Values<$modelName, $modelName.Properties>"
        },
        imports = { valuesImports },
        definitionModel = EmbeddedValuesDefinition.Model as IsSimpleObjectDataModel<EmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>,
        propertyValueOverride = mapOf(
            "default" to generateKotlinValueWithDefinition
        )
    ),
    PropertyDefinitionType.EmbedObject to PropertyDefinitionKotlinDescriptor(
        className = "EmbeddedObjectDefinition",
        kotlinTypeName = { it.dataModel.name },
        definitionModel = EmbeddedObjectDefinition.Model as IsSimpleObjectDataModel<EmbeddedObjectDefinition<Any, *, ObjectDataModel<Any, *>, *, *>>,
        propertyValueOverride = mapOf(
            "default" to generateKotlinValueWithDefinition
        )
    ),
    PropertyDefinitionType.Time to PropertyDefinitionKotlinDescriptor(
        className = "TimeDefinition",
        kotlinTypeName = { "Time" },
        imports = { timeImports },
        definitionModel = TimeDefinition.Model
    ),
    PropertyDefinitionType.Value to PropertyDefinitionKotlinDescriptor(
        className = "ValueModelDefinition",
        kotlinTypeName = { it.dataModel.name },
        definitionModel = ValueModelDefinition.Model as IsSimpleObjectDataModel<ValueModelDefinition<ValueDataObject, ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>, ObjectPropertyDefinitions<ValueDataObject>>>,
        propertyValueOverride = mapOf(
            "default" to generateKotlinValueWithDefinition,
            "minValue" to generateKotlinValueWithDefinition,
            "maxValue" to generateKotlinValueWithDefinition
        )
    )
)
