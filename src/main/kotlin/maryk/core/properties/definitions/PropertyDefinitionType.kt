package maryk.core.properties.definitions

import maryk.core.objects.AbstractDataModel
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
        PropertyDefinitionType.Boolean.index to SubModelDefinition(dataModel = { BooleanDefinition.Model }),
        PropertyDefinitionType.Date.index to SubModelDefinition(dataModel = { DateDefinition.Model }),
        PropertyDefinitionType.DateTime.index to SubModelDefinition(dataModel = { DateTimeDefinition.Model }),
        PropertyDefinitionType.Enum.index to SubModelDefinition<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, AbstractDataModel<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>(dataModel = { EnumDefinition.Model }),
        PropertyDefinitionType.FixedBytes.index to SubModelDefinition(dataModel = { FixedBytesDefinition.Model }),
        PropertyDefinitionType.FlexBytes.index to SubModelDefinition(dataModel = { FlexBytesDefinition.Model }),
        PropertyDefinitionType.List.index to SubModelDefinition(dataModel = { ListDefinition.Model }),
        PropertyDefinitionType.Map.index to SubModelDefinition(dataModel = { MapDefinition.Model }),
        PropertyDefinitionType.MultiType.index to SubModelDefinition(dataModel = { MultiTypeDefinition.Model }),
        PropertyDefinitionType.Number.index to SubModelDefinition<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, AbstractDataModel<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>, IsPropertyContext, NumericContext>(dataModel = { NumberDefinition.Model }),
        PropertyDefinitionType.Reference.index to SubModelDefinition(dataModel = { ReferenceDefinition.Model }),
        PropertyDefinitionType.Set.index to SubModelDefinition(dataModel = { SetDefinition.Model }),
        PropertyDefinitionType.String.index to SubModelDefinition(dataModel = { StringDefinition.Model }),
        PropertyDefinitionType.SubModel.index to SubModelDefinition(dataModel = { SubModelDefinition.Model }),
        PropertyDefinitionType.Time.index to SubModelDefinition(dataModel = { TimeDefinition.Model }),
        PropertyDefinitionType.ValueModel.index to SubModelDefinition(dataModel = { ValueModelDefinition.Model })
)

val propertyDefinitionWrapper = SubModelDefinition(dataModel = { PropertyDefinitionWrapper })
val fixedBytesPropertyDefinitionWrapper = SubModelDefinition(dataModel = { FixedBytesPropertyDefinitionWrapper })

internal val mapOfPropertyDefWrapperDefinitions = mapOf(
        PropertyDefinitionType.Boolean.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Date.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.DateTime.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Enum.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.FixedBytes.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.FlexBytes.index to propertyDefinitionWrapper,
        PropertyDefinitionType.List.index to SubModelDefinition(dataModel = { ListPropertyDefinitionWrapper }),
        PropertyDefinitionType.Map.index to SubModelDefinition(dataModel = { MapPropertyDefinitionWrapper }),
        PropertyDefinitionType.MultiType.index to propertyDefinitionWrapper,
        PropertyDefinitionType.Number.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Reference.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.Set.index to SubModelDefinition(dataModel = { SetPropertyDefinitionWrapper }),
        PropertyDefinitionType.String.index to propertyDefinitionWrapper,
        PropertyDefinitionType.SubModel.index to SubModelDefinition(dataModel = { SubModelPropertyDefinitionWrapper }),
        PropertyDefinitionType.Time.index to fixedBytesPropertyDefinitionWrapper,
        PropertyDefinitionType.ValueModel.index to propertyDefinitionWrapper
)
