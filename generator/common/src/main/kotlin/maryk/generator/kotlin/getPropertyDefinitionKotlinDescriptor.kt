package maryk.generator.kotlin

import maryk.core.objects.DataModel
import maryk.core.objects.IsDataModel
import maryk.core.objects.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueModelDefinition
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.NumberType

/** Get the PropertyDefinitionKotlinDescriptor of the given property */
internal fun <T: Any, D: IsTransportablePropertyDefinitionType<T>> D.getKotlinDescriptor(): PropertyDefinitionKotlinDescriptor<T, D> {
    @Suppress("UNCHECKED_CAST")
    return definitionNamesMap[this.propertyDefinitionType] as PropertyDefinitionKotlinDescriptor<T, D>?
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
        kotlinTypeName = { it: EnumDefinition<*> -> it.enum.name },
        definitionModel = EnumDefinition.Model as IsDataModel<EnumDefinition<IndexedEnum<IndexedEnum<*>>>>,
        propertyValueOverride = mapOf(
            "maxValue" to { definition, value ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "minValue" to { definition, value ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "default" to { definition, value ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum<*>).name}"
            },
            "values" to { definition, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.values()"
            },
            "name" to { definition, _ ->
                val enumDefinition = definition as EnumDefinition<*>

                enumDefinition.enum.name
            }
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
        definitionModel = ListDefinition.Model as IsDataModel<ListDefinition<Any, *>>
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
        definitionModel = MapDefinition.Model as IsDataModel<MapDefinition<Any, Any, *>>
    ),
    PropertyDefinitionType.MultiType to PropertyDefinitionKotlinDescriptor(
        className = "MultiTypeDefinition",
        kotlinTypeName = { "TypedValue<${it.typeEnum.name}, *>" },
        definitionModel = MultiTypeDefinition.Model as IsDataModel<MultiTypeDefinition<IndexedEnum<Any>, *>>,
        propertyValueOverride = mapOf(
            "definitionMap" to { definition, _ ->
                val multiTypeDefinition = definition as MultiTypeDefinition<IndexedEnum<IndexedEnum<*>>, IsPropertyContext>

                val typeName = multiTypeDefinition.typeEnum.name

                val typeValues = mutableListOf<String>()

                for (typeDefinition in multiTypeDefinition.definitionMap) {
                    @Suppress("UNCHECKED_CAST")
                    val value = typeDefinition.value as IsTransportablePropertyDefinitionType<Any>
                    val valueDefinition = value.getKotlinDescriptor()
                    val valueAsString = valueDefinition.definitionToKotlin(value, {}).trimStart()
                    typeValues.add("$typeName.${typeDefinition.key.name} to $valueAsString")
                }

                val types = typeValues.joinToString(",\n").prependIndent()

                "mapOf<$typeName, IsSubDefinition<*, IsPropertyContext>>(\n$types\n)"
            }
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
                NumberType.UInt8 -> "UInt8"
                NumberType.UInt16 -> "UInt16"
                NumberType.UInt32 -> "UInt32"
                NumberType.UInt64 -> "UInt64"
                NumberType.Float32 -> "Float"
                NumberType.Float64 -> "Float64"
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
        definitionModel = NumberDefinition.Model as IsDataModel<NumberDefinition<Comparable<Any>>>
    ),
    PropertyDefinitionType.Reference to PropertyDefinitionKotlinDescriptor(
        className = "ReferenceDefinition",
        kotlinTypeName = { "Key<${it.dataModel.name}>" },
        imports = { keyImports },
        definitionModel = ReferenceDefinition.Model as IsDataModel<ReferenceDefinition<Any>>
    ),
    PropertyDefinitionType.Set to PropertyDefinitionKotlinDescriptor(
        className = "SetDefinition",
        kotlinTypeName = {
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<Any>
            val kotlinDescriptorForValueDefinition = transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Set<$kotlinDescriptorForValueDefinition>"
        },
        definitionModel = SetDefinition.Model as IsDataModel<SetDefinition<Any, *>>
    ),
    PropertyDefinitionType.String to PropertyDefinitionKotlinDescriptor(
        className = "StringDefinition",
        kotlinTypeName = { "String" },
        definitionModel = StringDefinition.Model
    ),
    PropertyDefinitionType.SubModel to PropertyDefinitionKotlinDescriptor(
        className = "SubModelDefinition",
        kotlinTypeName = { it.dataModel.name },
        definitionModel = SubModelDefinition.Model as IsDataModel<SubModelDefinition<Any, *, DataModel<Any, *>, *, *>>
    ),
    PropertyDefinitionType.Time to PropertyDefinitionKotlinDescriptor(
        className = "TimeDefinition",
        kotlinTypeName = { "Time" },
        imports = { timeImports },
        definitionModel = TimeDefinition.Model
    ),
    PropertyDefinitionType.ValueModel to PropertyDefinitionKotlinDescriptor(
        className = "ValueModelDefinition",
        kotlinTypeName = { it -> it.dataModel.name },
        definitionModel = ValueModelDefinition.Model as IsDataModel<ValueModelDefinition<ValueDataObject, ValueDataModel<ValueDataObject, PropertyDefinitions<ValueDataObject>>>>
    )
)
