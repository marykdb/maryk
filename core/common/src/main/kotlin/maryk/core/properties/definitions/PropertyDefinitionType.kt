package maryk.core.properties.definitions

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.objects.ValuesImpl
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.query.DefinitionsContext
import maryk.json.MapType

/** Indexed type of property definitions */
enum class PropertyDefinitionType(
    override val index: Int
): IndexedEnum<PropertyDefinitionType>, MapType {
    Boolean(1),
    Date(2),
    DateTime(3),
    Enum(4),
    FixedBytes(5),
    FlexBytes(6),
    List(7),
    Map(8),
    MultiType(9),
    Number(10),
    Reference(11),
    Set(12),
    String(13),
    Embed(14),
    EmbedObject(15),
    Time(16),
    Value(17);

    companion object: IndexedEnumDefinition<PropertyDefinitionType>("PropertyDefinitionType", PropertyDefinitionType::values)
}

internal val mapOfPropertyDefEmbeddedObjectDefinitions = mapOf<PropertyDefinitionType, IsSubDefinition<out Any, DefinitionsContext>>(
    PropertyDefinitionType.Boolean to EmbeddedObjectDefinition(dataModel = { BooleanDefinition.Model }),
    PropertyDefinitionType.Date to EmbeddedObjectDefinition(dataModel = { DateDefinition.Model }),
    PropertyDefinitionType.DateTime to EmbeddedObjectDefinition(dataModel = { DateTimeDefinition.Model }),
    PropertyDefinitionType.Enum to EmbeddedObjectDefinition<EnumDefinition<*>, ObjectPropertyDefinitions<EnumDefinition<*>>, AbstractObjectDataModel<EnumDefinition<*>, ObjectPropertyDefinitions<EnumDefinition<*>>, DefinitionsContext, EnumDefinitionContext>, DefinitionsContext, EnumDefinitionContext>(dataModel = { EnumDefinition.Model }),
    PropertyDefinitionType.FixedBytes to EmbeddedObjectDefinition(dataModel = { FixedBytesDefinition.Model }),
    PropertyDefinitionType.FlexBytes to EmbeddedObjectDefinition(dataModel = { FlexBytesDefinition.Model }),
    PropertyDefinitionType.List to EmbeddedObjectDefinition(dataModel = { ListDefinition.Model }),
    PropertyDefinitionType.Map to EmbeddedObjectDefinition(dataModel = { MapDefinition.Model }),
    PropertyDefinitionType.MultiType to EmbeddedObjectDefinition(dataModel = {
        @Suppress("UNCHECKED_CAST")
        MultiTypeDefinition.Model as ContextualDataModel<MultiTypeDefinition<out IndexedEnum<Any>, *>, ObjectPropertyDefinitions<MultiTypeDefinition<out IndexedEnum<Any>, *>>, DefinitionsContext, MultiTypeDefinitionContext>
    }),
    PropertyDefinitionType.Number to EmbeddedObjectDefinition<NumberDefinition<*>, ObjectPropertyDefinitions<NumberDefinition<*>>, AbstractObjectDataModel<NumberDefinition<*>, ObjectPropertyDefinitions<NumberDefinition<*>>, IsPropertyContext, NumericContext>, IsPropertyContext, NumericContext>(dataModel = { NumberDefinition.Model }),
    PropertyDefinitionType.Reference to EmbeddedObjectDefinition(dataModel = { ReferenceDefinition.Model }),
    PropertyDefinitionType.Set to EmbeddedObjectDefinition(dataModel = { SetDefinition.Model }),
    PropertyDefinitionType.String to EmbeddedObjectDefinition(dataModel = { StringDefinition.Model }),
    PropertyDefinitionType.Embed to EmbeddedObjectDefinition(dataModel = { EmbeddedValuesDefinition.Model }),
    PropertyDefinitionType.EmbedObject to EmbeddedObjectDefinition(dataModel = { EmbeddedObjectDefinition.Model }),
    PropertyDefinitionType.Time to EmbeddedObjectDefinition(dataModel = { TimeDefinition.Model }),
    PropertyDefinitionType.Value to EmbeddedObjectDefinition(dataModel = { ValueModelDefinition.Model })
)

typealias WrapperCreator = (index: Int, name: String, definition: IsPropertyDefinition<Any>, getter: (Any) -> Any?) -> IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>

@Suppress("UNCHECKED_CAST")
val createFixedBytesWrapper: WrapperCreator = { index, name, definition, getter ->
    FixedBytesPropertyDefinitionWrapper(
        index,
        name,
        definition as IsSerializableFixedBytesEncodable<Any, IsPropertyContext>,
        getter
    )
}

@Suppress("UNCHECKED_CAST")
val createFlexBytesWrapper: WrapperCreator = { index, name, definition, getter ->
    PropertyDefinitionWrapper(
        index,
        name,
        definition as IsSerializableFlexBytesEncodable<Any, IsPropertyContext>,
        getter
    )
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
        ListPropertyDefinitionWrapper(
            index,
            name,
            definition as ListDefinition<Any, IsPropertyContext>,
            getter as (Any) -> List<Any>?
        )
    },
    PropertyDefinitionType.Map to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        MapPropertyDefinitionWrapper(
            index,
            name,
            definition as MapDefinition<Any, Any, IsPropertyContext>,
            getter as (Any) -> Map<Any, Any>?
        )
    },
    PropertyDefinitionType.MultiType to createFlexBytesWrapper,
    PropertyDefinitionType.Number to createFixedBytesWrapper,
    PropertyDefinitionType.Reference to createFixedBytesWrapper,
    PropertyDefinitionType.Set to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        SetPropertyDefinitionWrapper(
            index,
            name,
            definition as SetDefinition<Any, IsPropertyContext>,
            getter as (Any) -> Set<Any>?
        )
    },
    PropertyDefinitionType.String to createFlexBytesWrapper,
    PropertyDefinitionType.Embed to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        EmbeddedValuesPropertyDefinitionWrapper(
            index,
            name,
            definition as EmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>,
            getter as (Any) -> ValuesImpl?
        )
    },
    PropertyDefinitionType.EmbedObject to { index, name, definition, getter ->
        @Suppress("UNCHECKED_CAST")
        EmbeddedObjectPropertyDefinitionWrapper(
            index,
            name,
            definition as EmbeddedObjectDefinition<Any, ObjectPropertyDefinitions<Any>, AbstractObjectDataModel<Any, ObjectPropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>,
            getter
        )
    },
    PropertyDefinitionType.Time to createFixedBytesWrapper,
    PropertyDefinitionType.Value to createFixedBytesWrapper
)
