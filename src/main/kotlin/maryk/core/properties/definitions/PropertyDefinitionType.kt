package maryk.core.properties.definitions

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext
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


val a  = SubModelDefinition<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, AbstractDataModel<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>, IsPropertyContext, NumericContext>(dataModel = { NumberDefinition })

internal val mapOfPropertyDefSubModelDefinitions = mapOf(
        PropertyDefinitionType.Boolean.index to SubModelDefinition(dataModel = { BooleanDefinition }),
        PropertyDefinitionType.Date.index to SubModelDefinition(dataModel = { DateDefinition }),
        PropertyDefinitionType.DateTime.index to SubModelDefinition(dataModel = { DateTimeDefinition }),
        PropertyDefinitionType.Enum.index to SubModelDefinition<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, AbstractDataModel<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>(dataModel = { EnumDefinition }),
        PropertyDefinitionType.FixedBytes.index to SubModelDefinition(dataModel = { FixedBytesDefinition }),
        PropertyDefinitionType.FlexBytes.index to SubModelDefinition(dataModel = { FlexBytesDefinition }),
        PropertyDefinitionType.List.index to SubModelDefinition(dataModel = { ListDefinition }),
//        PropertyDefinitionType.Map.index to SubModelDefinition(dataModel = { MapDefinition }),
//        PropertyDefinitionType.MultiType.index to SubModelDefinition(dataModel = { MultiTypeDefinition }),
        PropertyDefinitionType.Number.index to SubModelDefinition<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, AbstractDataModel<NumberDefinition<*>, PropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>, IsPropertyContext, NumericContext>(dataModel = { NumberDefinition }),
//        PropertyDefinitionType.Reference.index to SubModelDefinition(dataModel = { ReferenceDefinition }),
        PropertyDefinitionType.Set.index to SubModelDefinition(dataModel = { SetDefinition }),
        PropertyDefinitionType.String.index to SubModelDefinition(dataModel = { StringDefinition }),
//        PropertyDefinitionType.SubModel.index to SubModelDefinition(dataModel = { SubModelDefinition }),
        PropertyDefinitionType.Time.index to SubModelDefinition(dataModel = { TimeDefinition })
//        PropertyDefinitionType.ValueModel.index to SubModelDefinition(dataModel = { ValueModelDefinition })
)
