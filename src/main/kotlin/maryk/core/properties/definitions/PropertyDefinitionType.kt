package maryk.core.properties.definitions

import maryk.core.json.ObjectType
import maryk.core.objects.AbstractDataModel
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.properties.types.IndexedEnum

/** Indexed type of property definitions */
enum class PropertyDefinitionType(
    override val index: Int
): IndexedEnum<PropertyDefinitionType>, ObjectType {
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

typealias WrapperCreator = (index: Int, name: String, definition: IsPropertyDefinition<Any>, getter: (Any) -> Any?) -> IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>

@Suppress("UNCHECKED_CAST")
val createFixedBytesWrapper: WrapperCreator = { index, name, definition, getter ->
    FixedBytesPropertyDefinitionWrapper(index, name, definition as IsSerializableFixedBytesEncodable<Any, IsPropertyContext>, getter)
}

@Suppress("UNCHECKED_CAST")
val createFlexBytesWrapper: WrapperCreator = { index, name, definition, getter ->
    PropertyDefinitionWrapper(index, name, definition as IsSerializableFlexBytesEncodable<Any, IsPropertyContext>, getter)
}

internal val mapOfPropertyDefWrappers = mapOf(
    PropertyDefinitionType.Boolean to createFixedBytesWrapper,
    PropertyDefinitionType.Date to createFixedBytesWrapper,
    PropertyDefinitionType.DateTime to createFixedBytesWrapper,
    PropertyDefinitionType.Enum to createFixedBytesWrapper,
    PropertyDefinitionType.FixedBytes to createFixedBytesWrapper,
    PropertyDefinitionType.FlexBytes to createFlexBytesWrapper,
    PropertyDefinitionType.List to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        ListPropertyDefinitionWrapper(index, name, definition as ListDefinition<Any, IsPropertyContext>, getter as (Any) -> List<Any>?)
    },
    PropertyDefinitionType.Map to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        MapPropertyDefinitionWrapper(index, name, definition as MapDefinition<Any, Any, IsPropertyContext>, getter as (Any) -> Map<Any, Any>?)
    },
    PropertyDefinitionType.MultiType to createFlexBytesWrapper,
    PropertyDefinitionType.Number to createFixedBytesWrapper,
    PropertyDefinitionType.Reference to createFixedBytesWrapper,
    PropertyDefinitionType.Set to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        SetPropertyDefinitionWrapper(index, name, definition as SetDefinition<Any, IsPropertyContext>, getter as (Any) -> Set<Any>?)
    },
    PropertyDefinitionType.String to createFlexBytesWrapper,
    PropertyDefinitionType.SubModel to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        SubModelPropertyDefinitionWrapper(index, name, definition as SubModelDefinition<Any, PropertyDefinitions<Any>, AbstractDataModel<Any, PropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>, getter as (Any) -> Set<Any>?)
    },
    PropertyDefinitionType.Time to createFixedBytesWrapper,
    PropertyDefinitionType.ValueModel to createFixedBytesWrapper
)
