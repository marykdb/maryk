package maryk.core.properties.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.ContainsDefinitionsContext
import maryk.json.MapType

/** Indexed type of property definitions */
enum class PropertyDefinitionType(
    override val index: UInt,
    override val alternativeNames: kotlin.collections.Set<kotlin.String>? = null
) : IndexedEnumComparable<PropertyDefinitionType>, MapType, IsCoreEnum, TypeEnum<IsTransportablePropertyDefinitionType<*>> {
    Boolean(1u),
    Date(2u),
    DateTime(3u),
    Enum(4u),
    FixedBytes(5u),
    FlexBytes(6u),
    List(7u),
    Map(8u),
    MultiType(9u),
    Number(10u),
    Reference(11u),
    Set(12u),
    String(13u),
    Embed(14u),
    Time(15u),
    Value(16u);

    companion object :
        IndexedEnumDefinition<PropertyDefinitionType>("PropertyDefinitionType", PropertyDefinitionType::values)
}

internal val mapOfPropertyDefEmbeddedObjectDefinitions =
    mapOf<PropertyDefinitionType, IsSubDefinition<out Any, ContainsDefinitionsContext>>(
        PropertyDefinitionType.Boolean to EmbeddedObjectDefinition(dataModel = { BooleanDefinition.Model }),
        PropertyDefinitionType.Date to EmbeddedObjectDefinition(dataModel = { DateDefinition.Model }),
        PropertyDefinitionType.DateTime to EmbeddedObjectDefinition(dataModel = { DateTimeDefinition.Model }),
        PropertyDefinitionType.Enum to EmbeddedObjectDefinition(dataModel = { EnumDefinition.Model }),
        PropertyDefinitionType.FixedBytes to EmbeddedObjectDefinition(dataModel = { FixedBytesDefinition.Model }),
        PropertyDefinitionType.FlexBytes to EmbeddedObjectDefinition(dataModel = { FlexBytesDefinition.Model }),
        PropertyDefinitionType.List to EmbeddedObjectDefinition(dataModel = { ListDefinition.Model }),
        PropertyDefinitionType.Map to EmbeddedObjectDefinition(dataModel = { MapDefinition.Model }),
        PropertyDefinitionType.MultiType to EmbeddedObjectDefinition(dataModel = { MultiTypeDefinition.Model }),
        PropertyDefinitionType.Number to EmbeddedObjectDefinition(dataModel = { NumberDefinition.Model }),
        PropertyDefinitionType.Reference to EmbeddedObjectDefinition(dataModel = { ReferenceDefinition.Model }),
        PropertyDefinitionType.Set to EmbeddedObjectDefinition(dataModel = { SetDefinition.Model }),
        PropertyDefinitionType.String to EmbeddedObjectDefinition(dataModel = { StringDefinition.Model }),
        PropertyDefinitionType.Embed to EmbeddedObjectDefinition(dataModel = { EmbeddedValuesDefinition.Model }),
        PropertyDefinitionType.Time to EmbeddedObjectDefinition(dataModel = { TimeDefinition.Model }),
        PropertyDefinitionType.Value to EmbeddedObjectDefinition(dataModel = { ValueModelDefinition.Model })
    )

typealias WrapperCreator = (index: UInt, name: String, altNames: Set<String>?, definition: IsPropertyDefinition<out Any>) -> IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>

@Suppress("UNCHECKED_CAST")
val createFixedBytesWrapper: WrapperCreator = { index, name, altNames, definition ->
    FixedBytesDefinitionWrapper(
        index,
        name,
        definition as IsSerializableFixedBytesEncodable<Any, IsPropertyContext>,
        altNames
    )
}

@Suppress("UNCHECKED_CAST")
val createFlexBytesWrapper: WrapperCreator = { index, name, altNames, definition ->
    FlexBytesDefinitionWrapper(
        index,
        name,
        definition as IsSerializableFlexBytesEncodable<Any, IsPropertyContext>,
        altNames
    )
}

internal val mapOfPropertyDefWrappers = mapOf(
    PropertyDefinitionType.Boolean to createFixedBytesWrapper,
    PropertyDefinitionType.Date to createFixedBytesWrapper,
    PropertyDefinitionType.DateTime to createFixedBytesWrapper,
    PropertyDefinitionType.Enum to createFixedBytesWrapper,
    PropertyDefinitionType.FixedBytes to createFixedBytesWrapper,
    PropertyDefinitionType.FlexBytes to createFlexBytesWrapper,
    PropertyDefinitionType.List to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        ListDefinitionWrapper<Any, List<Any>, IsPropertyContext, Any>(
            index,
            name,
            definition as ListDefinition<Any, IsPropertyContext>,
            altNames
        )
    },
    PropertyDefinitionType.Map to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        MapDefinitionWrapper(
            index,
            name,
            definition as MapDefinition<Any, Any, IsPropertyContext>,
            altNames
        )
    },
    PropertyDefinitionType.MultiType to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        MultiTypeDefinitionWrapper(
            index,
            name,
            definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
            altNames
        )
    },
    PropertyDefinitionType.Number to createFixedBytesWrapper,
    PropertyDefinitionType.Reference to createFixedBytesWrapper,
    PropertyDefinitionType.Set to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        SetDefinitionWrapper(
            index,
            name,
            definition as SetDefinition<Any, IsPropertyContext>,
            altNames
        )
    },
    PropertyDefinitionType.String to createFlexBytesWrapper,
    PropertyDefinitionType.Embed to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        EmbeddedValuesDefinitionWrapper(
            index,
            name,
            definition as EmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>,
            altNames
        )
    },
    PropertyDefinitionType.Time to createFixedBytesWrapper,
    PropertyDefinitionType.Value to createFixedBytesWrapper
)
