package maryk.core.properties.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
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
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<PropertyDefinitionType>,
    MapType,
    IsCoreEnum,
    TypeEnum<IsTransportablePropertyDefinitionType<*>> {

    Boolean(1u),
    Date(2u),
    DateTime(3u),
    Embed(4u),
    Enum(5u),
    FixedBytes(6u),
    FlexBytes(7u),
    GeoPoint(8u),
    IncMap(9u),
    List(10u),
    Map(11u),
    MultiType(12u),
    Number(13u),
    Reference(14u),
    Set(15u),
    String(16u),
    Time(17u),
    Value(18u);

    companion object : IndexedEnumDefinition<PropertyDefinitionType>(PropertyDefinitionType::class, { entries })
}

internal val mapOfPropertyDefEmbeddedObjectDefinitions =
    mapOf<PropertyDefinitionType, IsUsableInMultiType<out Any, ContainsDefinitionsContext>>(
        PropertyDefinitionType.Boolean to EmbeddedObjectDefinition(dataModel = { BooleanDefinition.Model }),
        PropertyDefinitionType.Date to EmbeddedObjectDefinition(dataModel = { DateDefinition.Model }),
        PropertyDefinitionType.DateTime to EmbeddedObjectDefinition(dataModel = { DateTimeDefinition.Model }),
        PropertyDefinitionType.Embed to EmbeddedObjectDefinition(dataModel = { EmbeddedValuesDefinition.Model }),
        PropertyDefinitionType.Enum to EmbeddedObjectDefinition(dataModel = { EnumDefinition.Model }),
        PropertyDefinitionType.FixedBytes to EmbeddedObjectDefinition(dataModel = { FixedBytesDefinition.Model }),
        PropertyDefinitionType.FlexBytes to EmbeddedObjectDefinition(dataModel = { FlexBytesDefinition.Model }),
        PropertyDefinitionType.GeoPoint to EmbeddedObjectDefinition(dataModel = { GeoPointDefinition.Model }),
        PropertyDefinitionType.IncMap to EmbeddedObjectDefinition(dataModel = { IncrementingMapDefinition.Model }),
        PropertyDefinitionType.List to EmbeddedObjectDefinition(dataModel = { ListDefinition.Model }),
        PropertyDefinitionType.Map to EmbeddedObjectDefinition(dataModel = { MapDefinition.Model }),
        PropertyDefinitionType.MultiType to EmbeddedObjectDefinition(dataModel = { MultiTypeDefinition.Model }),
        PropertyDefinitionType.Number to EmbeddedObjectDefinition(dataModel = { NumberDefinition.Model }),
        PropertyDefinitionType.Reference to EmbeddedObjectDefinition(dataModel = { ReferenceDefinition.Model }),
        PropertyDefinitionType.Set to EmbeddedObjectDefinition(dataModel = { SetDefinition.Model }),
        PropertyDefinitionType.String to EmbeddedObjectDefinition(dataModel = { StringDefinition.Model }),
        PropertyDefinitionType.Time to EmbeddedObjectDefinition(dataModel = { TimeDefinition.Model }),
        PropertyDefinitionType.Value to EmbeddedObjectDefinition(dataModel = { ValueObjectDefinition.Model })
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
    PropertyDefinitionType.Embed to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        EmbeddedValuesDefinitionWrapper(
            index,
            name,
            definition as EmbeddedValuesDefinition<IsValuesDataModel>,
            altNames
        )
    },
    PropertyDefinitionType.Enum to createFixedBytesWrapper,
    PropertyDefinitionType.FixedBytes to createFixedBytesWrapper,
    PropertyDefinitionType.FlexBytes to createFlexBytesWrapper,
    PropertyDefinitionType.GeoPoint to createFixedBytesWrapper,
    PropertyDefinitionType.IncMap to { index, name, altNames, definition ->
        @Suppress("UNCHECKED_CAST")
        MapDefinitionWrapper(
            index,
            name,
            definition as IncrementingMapDefinition<Comparable<Any>, Any, IsPropertyContext>,
            altNames
        )
    },
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
    PropertyDefinitionType.Time to createFixedBytesWrapper,
    PropertyDefinitionType.Value to createFixedBytesWrapper
)
