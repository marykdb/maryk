package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values

object ComplexModel : RootDataModel<ComplexModel>() {
    val multi by multiType(
        index = 1u,
        required = false,
        typeEnum = MarykTypeEnum,
        typeIsFinal = false
    )

    val mapStringString by map(
        index = 2u,
        required = false,
        minSize = 1u,
        maxSize = 3u,
        keyDefinition = StringDefinition(
            minSize = 1u,
            maxSize = 20u
        ),
        valueDefinition = StringDefinition(
            maxSize = 500u
        )
    )

    val mapIntObject by map(
        index = 3u,
        required = false,
        keyDefinition = NumberDefinition(
            type = UInt32
        ),
        valueDefinition = EmbeddedValuesDefinition(
            dataModel = { EmbeddedMarykModel }
        )
    )

    val mapIntMulti by map(
        index = 4u,
        required = false,
        keyDefinition = NumberDefinition(
            type = UInt32
        ),
        valueDefinition = MultiTypeDefinition(
            typeEnum = MarykTypeEnum
        )
    )

    val mapWithList by map(
        index = 5u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = ListDefinition(
            valueDefinition = StringDefinition()
        )
    )

    val mapWithSet by map(
        index = 6u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = SetDefinition(
            valueDefinition = StringDefinition()
        )
    )

    val mapWithMap by map(
        index= 7u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = MapDefinition(
            keyDefinition = StringDefinition(),
            valueDefinition = StringDefinition()
        )
    )

    val incMap by incrementingMap(
        index = 8u,
        required = false,
        keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedValuesDefinition(
            dataModel = { EmbeddedMarykModel }
        )
    )

    operator fun invoke(
        multi: TypedValue<MarykTypeEnum<*>, Any>? = null,
        mapStringString: Map<String, String>? = null,
        mapIntObject: Map<UInt, Values<EmbeddedMarykModel>>? = null,
        mapIntMulti: Map<UInt, TypedValue<MarykTypeEnum<*>, Any>>? = null,
        mapWithList: Map<String, List<String>>? = null,
        mapWithSet: Map<String, Set<String>>? = null,
        mapWithMap: Map<String, Map<String, String>>? = null,
        incMap: Map<UInt, Values<EmbeddedMarykModel>>? = null
    ) = create {
        this.multi += multi
        this.mapStringString += mapStringString
        this.mapIntObject += mapIntObject
        this.mapIntMulti += mapIntMulti
        this.mapWithList += mapWithList
        this.mapWithSet += mapWithSet
        this.mapWithMap += mapWithMap
        this.incMap += incMap
    }
}
