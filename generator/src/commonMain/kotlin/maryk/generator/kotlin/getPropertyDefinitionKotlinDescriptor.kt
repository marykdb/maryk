package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
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
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.types.numeric.NumberType.Float32Type
import maryk.core.properties.types.numeric.NumberType.Float64Type
import maryk.core.properties.types.numeric.NumberType.SInt16Type
import maryk.core.properties.types.numeric.NumberType.SInt32Type
import maryk.core.properties.types.numeric.NumberType.SInt64Type
import maryk.core.properties.types.numeric.NumberType.SInt8Type
import maryk.core.properties.types.numeric.NumberType.UInt16Type
import maryk.core.properties.types.numeric.NumberType.UInt32Type
import maryk.core.properties.types.numeric.NumberType.UInt64Type
import maryk.core.properties.types.numeric.NumberType.UInt8Type
import kotlin.native.concurrent.SharedImmutable

/** Get the PropertyDefinitionKotlinDescriptor of the given property */
internal fun <T : Any, D : IsTransportablePropertyDefinitionType<in T>, P : ObjectPropertyDefinitions<D>> D.getKotlinDescriptor(): PropertyDefinitionKotlinDescriptor<T, D, P> {
    @Suppress("UNCHECKED_CAST")
    return definitionNamesMap[this.propertyDefinitionType] as PropertyDefinitionKotlinDescriptor<T, D, P>?
        ?: throw TypeException("Unknown propertyDefinitionType ${this.propertyDefinitionType}")
}

@SharedImmutable
private val dateImports = arrayOf("kotlinx.datetime.LocalDate")
@SharedImmutable
private val dateTimeImports = arrayOf("kotlinx.datetime.LocalDateTime")
@SharedImmutable
private val geoPointImports = arrayOf("maryk.core.properties.types.GeoPoint")
@SharedImmutable
private val timeImports = arrayOf("kotlinx.datetime.LocalTime")
@SharedImmutable
private val multiTypeImports = arrayOf("maryk.core.properties.types.TypedValue")
@SharedImmutable
private val keyImports = arrayOf("maryk.core.properties.types.Key")
@SharedImmutable
private val uInt8Imports = arrayOf("maryk.core.properties.types.numeric.UInt8")
@SharedImmutable
private val uInt16Imports = arrayOf("maryk.core.properties.types.numeric.UInt16")
@SharedImmutable
private val uInt32Imports = arrayOf("maryk.core.properties.types.numeric.UInt32")
@SharedImmutable
private val uInt64Imports = arrayOf("maryk.core.properties.types.numeric.UInt64")
@SharedImmutable
private val sInt8Imports = arrayOf("maryk.core.properties.types.numeric.SInt8")
@SharedImmutable
private val sInt16Imports = arrayOf("maryk.core.properties.types.numeric.SInt16")
@SharedImmutable
private val sInt32Imports = arrayOf("maryk.core.properties.types.numeric.SInt32")
@SharedImmutable
private val sInt64Imports = arrayOf("maryk.core.properties.types.numeric.SInt64")
@SharedImmutable
private val float32Imports = arrayOf("maryk.core.properties.types.numeric.Float32")
@SharedImmutable
private val float64Imports = arrayOf("maryk.core.properties.types.numeric.Float64")
@SharedImmutable
private val valuesImports = arrayOf("maryk.core.values.Values")

@SharedImmutable
private val generateKotlinValueWithDefinition: (IsTransportablePropertyDefinitionType<*>, Any, (String) -> Unit) -> String =
    { definition, value, addImport ->
        generateKotlinValue(definition, value, addImport)
    }

@SharedImmutable
private val definitionNamesMap = mapOf(
    PropertyDefinitionType.Boolean to PropertyDefinitionKotlinDescriptor(
        className = "BooleanDefinition",
        wrapFunctionName = "boolean",
        kotlinTypeName = { "Boolean" },
        definitionModel = BooleanDefinition.Model
    ),
    PropertyDefinitionType.Date to PropertyDefinitionKotlinDescriptor(
        className = "DateDefinition",
        wrapFunctionName = "date",
        kotlinTypeName = { "LocalDate" },
        definitionModel = DateDefinition.Model,
        imports = { dateImports }
    ),
    PropertyDefinitionType.DateTime to PropertyDefinitionKotlinDescriptor(
        className = "DateTimeDefinition",
        wrapFunctionName = "dateTime",
        kotlinTypeName = { "LocalDateTime" },
        definitionModel = DateTimeDefinition.Model,
        imports = { dateTimeImports }
    ),
    PropertyDefinitionType.Enum to PropertyDefinitionKotlinDescriptor(
        className = "EnumDefinition",
        wrapFunctionName = "enum",
        kotlinTypeName = { it.enum.name },
        definitionModel = EnumDefinition.Model,
        propertyValueOverride = mapOf(
            "maxValue" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum).name}"
            },
            "minValue" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum).name}"
            },
            "default" to { definition, value, _ ->
                val enumDefinition = definition as EnumDefinition<*>
                "${enumDefinition.enum.name}.${(value as IndexedEnum).name}"
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
        wrapFunctionName = "fixedBytes",
        kotlinTypeName = { "Bytes" },
        definitionModel = FixedBytesDefinition.Model
    ),
    PropertyDefinitionType.FlexBytes to PropertyDefinitionKotlinDescriptor(
        className = "FlexBytesDefinition",
        wrapFunctionName = "flexBytes",
        kotlinTypeName = { "Bytes" },
        definitionModel = FlexBytesDefinition.Model
    ),
    PropertyDefinitionType.GeoPoint to PropertyDefinitionKotlinDescriptor(
        className = "GeoPointDefinition",
        wrapFunctionName = "geoPoint",
        kotlinTypeName = { "GeoPoint" },
        definitionModel = GeoPointDefinition.Model,
        imports = { geoPointImports }
    ),
    PropertyDefinitionType.IncMap to PropertyDefinitionKotlinDescriptor(
        className = "IncrementingMapDefinition",
        wrapFunctionName = "incrementingMap",
        kotlinTypeName = {
            val transportableKeyDefinition = it.keyDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForKeyDefinition =
                transportableKeyDefinition.getKotlinDescriptor().kotlinTypeName(transportableKeyDefinition)
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForValueDefinition =
                transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Map<$kotlinDescriptorForKeyDefinition, $kotlinDescriptorForValueDefinition>"
        },
        propertyValueOverride = mapOf(
            "keyDefinition" to { definition, _, _ ->
                val keyDefinition = definition as IncrementingMapDefinition<*, *, *>
                keyDefinition.keyNumberDescriptor.type.name
            }
        ),
        propertyNameOverride = mapOf("keyDefinition" to "keyNumberDescriptor"),
        definitionModel = IncrementingMapDefinition.Model
    ),
    PropertyDefinitionType.List to PropertyDefinitionKotlinDescriptor(
        className = "ListDefinition",
        wrapFunctionName = "list",
        kotlinTypeName = {
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForValueDefinition =
                transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "List<$kotlinDescriptorForValueDefinition>"
        },
        definitionModel = ListDefinition.Model
    ),
    PropertyDefinitionType.Map to PropertyDefinitionKotlinDescriptor(
        className = "MapDefinition",
        wrapFunctionName = "map",
        kotlinTypeName = {
            val transportableKeyDefinition = it.keyDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForKeyDefinition =
                transportableKeyDefinition.getKotlinDescriptor().kotlinTypeName(transportableKeyDefinition)
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForValueDefinition =
                transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Map<$kotlinDescriptorForKeyDefinition, $kotlinDescriptorForValueDefinition>"
        },
        definitionModel = MapDefinition.Model
    ),
    PropertyDefinitionType.MultiType to PropertyDefinitionKotlinDescriptor(
        className = "MultiTypeDefinition",
        wrapFunctionName = "multiType",
        kotlinTypeName = { "TypedValue<${it.typeEnum.name}<out Any>, Any>" },
        definitionModel = MultiTypeDefinition.Model,
        imports = { multiTypeImports },
        propertyValueOverride = mapOf(
            "definitionMap" to { definition, _, _ ->
                val multiTypeDefinition =
                    definition as MultiTypeDefinition<*, *>

                val typeName = multiTypeDefinition.typeEnum.name

                val typeValues = mutableListOf<String>()

                for (type in multiTypeDefinition.typeEnum.cases()) {
                    val value = type.definition as IsTransportablePropertyDefinitionType<*>
                    val valueDefinition = value.getKotlinDescriptor()
                    val valueAsString = valueDefinition.definitionToKotlin(value) {}.trimStart()
                    typeValues.add("$typeName.${type.name} to $valueAsString")
                }

                val types = typeValues.joinToString(",\n").prependIndent()

                "definitionMap(\n$types\n)"
            },
            "default" to generateKotlinValueWithDefinition
        )
    ),
    PropertyDefinitionType.Number to PropertyDefinitionKotlinDescriptor(
        className = "NumberDefinition",
        wrapFunctionName = "number",
        kotlinTypeName = {
            when (it.type.type) {
                SInt8Type -> "Byte"
                SInt16Type -> "Short"
                SInt32Type -> "Int"
                SInt64Type -> "Long"
                UInt8Type -> "UByte"
                UInt16Type -> "UShort"
                UInt32Type -> "UInt"
                UInt64Type -> "ULong"
                Float32Type -> "Float"
                Float64Type -> "Double"
            }
        },
        imports = {
            when (it.type.type) {
                UInt8Type -> uInt8Imports
                UInt16Type -> uInt16Imports
                UInt32Type -> uInt32Imports
                UInt64Type -> uInt64Imports
                SInt8Type -> sInt8Imports
                SInt16Type -> sInt16Imports
                SInt32Type -> sInt32Imports
                SInt64Type -> sInt64Imports
                Float32Type -> float32Imports
                Float64Type -> float64Imports
            }
        },
        definitionModel = NumberDefinition.Model
    ),
    PropertyDefinitionType.Reference to PropertyDefinitionKotlinDescriptor(
        className = "ReferenceDefinition",
        wrapFunctionName = "reference",
        kotlinTypeName = { "Key<${it.dataModel.Model.name}>" },
        imports = { keyImports },
        definitionModel = ReferenceDefinition.Model
    ),
    PropertyDefinitionType.Set to PropertyDefinitionKotlinDescriptor(
        className = "SetDefinition",
        wrapFunctionName = "set",
        kotlinTypeName = {
            val transportableValueDefinition = it.valueDefinition as IsTransportablePropertyDefinitionType<*>
            val kotlinDescriptorForValueDefinition =
                transportableValueDefinition.getKotlinDescriptor().kotlinTypeName(transportableValueDefinition)
            "Set<$kotlinDescriptorForValueDefinition>"
        },
        definitionModel = SetDefinition.Model
    ),
    PropertyDefinitionType.String to PropertyDefinitionKotlinDescriptor(
        className = "StringDefinition",
        wrapFunctionName = "string",
        kotlinTypeName = { "String" },
        definitionModel = StringDefinition.Model
    ),
    PropertyDefinitionType.Embed to PropertyDefinitionKotlinDescriptor(
        className = "EmbeddedValuesDefinition",
        wrapFunctionName = "embed",
        kotlinTypeName = {
            val modelName = (it.dataModel as IsNamedDataModel<*>).name
            "Values<$modelName, $modelName.Properties>"
        },
        imports = { valuesImports },
        definitionModel = EmbeddedValuesDefinition.Model,
        propertyValueOverride = mapOf(
            "default" to generateKotlinValueWithDefinition
        )
    ),
    PropertyDefinitionType.Time to PropertyDefinitionKotlinDescriptor(
        className = "TimeDefinition",
        wrapFunctionName = "time",
        kotlinTypeName = { "LocalTime" },
        imports = { timeImports },
        definitionModel = TimeDefinition.Model
    ),
    PropertyDefinitionType.Value to PropertyDefinitionKotlinDescriptor(
        className = "ValueObjectDefinition",
        wrapFunctionName = "valueObject",
        kotlinTypeName = { it.dataModel.name },
        definitionModel = ValueObjectDefinition.Model,
        propertyValueOverride = mapOf(
            "default" to generateKotlinValueWithDefinition,
            "minValue" to generateKotlinValueWithDefinition,
            "maxValue" to generateKotlinValueWithDefinition
        )
    )
)
