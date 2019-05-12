package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.test.models.ComplexModel.Properties
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6
import maryk.test.models.MarykTypeEnum.T7
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S2
import maryk.test.models.SimpleMarykTypeEnum.S3

object ComplexModel : RootDataModel<ComplexModel, Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val multi = add(
            index = 1u, name = "multi",
            definition = MultiTypeDefinition<MarykTypeEnum<*>, Any, IsPropertyContext>(
                required = false,
                typeEnum = MarykTypeEnum,
                typeIsFinal = false,
                definitionMap = mapOf(
                    T1 to StringDefinition(),
                    T2 to NumberDefinition(type = SInt32),
                    T3 to EmbeddedValuesDefinition(
                        dataModel = { EmbeddedMarykModel }
                    )
                )
            )
        )

        val mapStringString = add(
            index = 2u, name = "mapStringString",
            definition = MapDefinition(
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
        )

        val mapIntObject = add(
            index = 3u, name = "mapIntObject",
            definition = MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = EmbeddedValuesDefinition(
                    dataModel = { EmbeddedMarykModel }
                )
            )
        )

        @Suppress("RemoveExplicitTypeArguments")
        val mapIntMulti = add(
            index = 4u, name = "mapIntMulti",
            definition = MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = MarykTypeEnum,
                    definitionMap = definitionMap(
                        T1 to StringDefinition(),
                        T2 to NumberDefinition(type = SInt32),
                        T3 to EmbeddedValuesDefinition(
                            dataModel = { EmbeddedMarykModel }
                        ),
                        T4 to ListDefinition(
                            valueDefinition = StringDefinition()
                        ),
                        T5 to SetDefinition(
                            valueDefinition = StringDefinition()
                        ),
                        T6 to MapDefinition(
                            keyDefinition = NumberDefinition(type = UInt32),
                            valueDefinition = StringDefinition()
                        ),
                        T7 to MultiTypeDefinition<SimpleMarykTypeEnum<*>, Any, IsPropertyContext>(
                            typeEnum = SimpleMarykTypeEnum,
                            definitionMap = mapOf(
                                S1 to StringDefinition(),
                                S2 to NumberDefinition(type = SInt16),
                                S3 to EmbeddedValuesDefinition(
                                    dataModel = { EmbeddedMarykModel }
                                )
                            )
                        )
                    )
                )
            )
        )

        val mapWithList = add(
            index = 5u, name = "mapWithList",
            definition = MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = ListDefinition(
                    valueDefinition = StringDefinition()
                )
            )
        )

        val mapWithSet = add(
            index = 6u, name = "mapWithSet",
            definition = MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = SetDefinition(
                    valueDefinition = StringDefinition()
                )
            )
        )

        val mapWithMap = add(
            index = 7u, name = "mapWithMap",
            definition = MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = MapDefinition(
                    keyDefinition = StringDefinition(),
                    valueDefinition = StringDefinition()
                )
            )
        )
    }

    operator fun invoke(
        multi: TypedValue<MarykTypeEnum<*>, Any>? = null,
        mapStringString: Map<String, String>? = null,
        mapIntObject: Map<UInt, Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>? = null,
        mapIntMulti: Map<UInt, TypedValue<MarykTypeEnum<*>, Any>>? = null,
        mapWithList: Map<String, List<String>>? = null,
        mapWithSet: Map<String, Set<String>>? = null,
        mapWithMap: Map<String, Map<String, String>>? = null
    ) = this.values {
        mapNonNulls(
            this.multi with multi,
            this.mapStringString with mapStringString,
            this.mapIntObject with mapIntObject,
            this.mapIntMulti with mapIntMulti,
            this.mapWithList with mapWithList,
            this.mapWithSet with mapWithSet,
            this.mapWithMap with mapWithMap
        )
    }
}
