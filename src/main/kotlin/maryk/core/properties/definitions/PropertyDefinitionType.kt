package maryk.core.properties.definitions

import maryk.core.objects.AbstractDataModel
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.properties.types.IndexedEnum

/** Indexed type of property definitions */
enum class PropertyDefinitionType(
        override val index: Int
): IndexedEnum<PropertyDefinitionType> {
    Boolean(0),
    Date(1),
    DateTime(2),
    Enum(3),
    FixedBytes(4),
    FlexBytes(5),
    List(6),
    Map(7),
    MultiType(8),
    Number(9),
    Reference(10),
    Set(11),
    String(12),
    SubModel(13),
    Time(14),
    ValueModel(15)
}

internal val mapOfPropertyDefSubModelDefinitions = mapOf(
        PropertyDefinitionType.Boolean to SubModelDefinition(dataModel = { BooleanDefinition.Model }),
        PropertyDefinitionType.Date to SubModelDefinition(dataModel = { DateDefinition.Model }),
        PropertyDefinitionType.DateTime to SubModelDefinition(dataModel = { DateTimeDefinition.Model }),
        PropertyDefinitionType.Enum to SubModelDefinition<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, AbstractDataModel<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>(dataModel = { EnumDefinition.Model }),
        PropertyDefinitionType.FixedBytes to SubModelDefinition(dataModel = { FixedBytesDefinition.Model }),
        PropertyDefinitionType.FlexBytes to SubModelDefinition(dataModel = { FlexBytesDefinition.Model }),
        PropertyDefinitionType.List to SubModelDefinition(dataModel = { ListDefinition.Model }),
        PropertyDefinitionType.Map to SubModelDefinition(dataModel = { MapDefinition.Model }),
        PropertyDefinitionType.MultiType to SubModelDefinition(dataModel = {
            @Suppress("UNCHECKED_CAST")
            MultiTypeDefinition.Model as SimpleDataModel<MultiTypeDefinition<out IndexedEnum<Any>, *>, PropertyDefinitions<MultiTypeDefinition<out IndexedEnum<Any>, *>>>
        }),
        PropertyDefinitionType.Number to SubModelDefinition<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, AbstractDataModel<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>, IsPropertyContext, NumericContext>(dataModel = { NumberDefinition.Model }),
        PropertyDefinitionType.Reference to SubModelDefinition(dataModel = { ReferenceDefinition.Model }),
        PropertyDefinitionType.Set to SubModelDefinition(dataModel = { SetDefinition.Model }),
        PropertyDefinitionType.String to SubModelDefinition(dataModel = { StringDefinition.Model }),
        PropertyDefinitionType.SubModel to SubModelDefinition(dataModel = { SubModelDefinition.Model }),
        PropertyDefinitionType.Time to SubModelDefinition(dataModel = { TimeDefinition.Model }),
        PropertyDefinitionType.ValueModel to SubModelDefinition(dataModel = { ValueModelDefinition.Model })
)

val propertyDefinitionWrapper = SubModelDefinition(dataModel = { PropertyDefinitionWrapper })
val fixedBytesPropertyDefinitionWrapper = SubModelDefinition(dataModel = { FixedBytesPropertyDefinitionWrapper })

internal val mapOfPropertyDefWrapperDefinitions = mapOf(
        PropertyDefinitionType.Boolean to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Date to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.DateTime to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Enum to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.FixedBytes to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.FlexBytes to propertyDefinitionWrapper,
        PropertyDefinitionType.List to SubModelDefinition(dataModel = { ListPropertyDefinitionWrapper }),
        PropertyDefinitionType.Map to SubModelDefinition(dataModel = { MapPropertyDefinitionWrapper }),
        PropertyDefinitionType.MultiType to propertyDefinitionWrapper,
        PropertyDefinitionType.Number to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Reference to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Set to SubModelDefinition(dataModel = { SetPropertyDefinitionWrapper }),
        PropertyDefinitionType.String to propertyDefinitionWrapper,
        PropertyDefinitionType.SubModel to SubModelDefinition(dataModel = { SubModelPropertyDefinitionWrapper }),
        PropertyDefinitionType.Time to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.ValueModel to fixedBytesPropertyDefinitionWrapper
)
