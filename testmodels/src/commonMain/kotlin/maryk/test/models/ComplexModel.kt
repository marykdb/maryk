package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.test.models.ComplexModel.Properties

object ComplexModel : RootDataModel<ComplexModel, Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val multi by wrap(1u) {
            MultiTypeDefinition(
                required = false,
                typeEnum = MarykTypeEnum,
                typeIsFinal = false
            )
        }

        val mapStringString by wrap(2u) {
            MapDefinition(
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
        }

        val mapIntObject by wrap(3u) {
            MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = EmbeddedValuesDefinition(
                    dataModel = { EmbeddedMarykModel }
                )
            )
        }

        @Suppress("RemoveExplicitTypeArguments")
        val mapIntMulti by wrap(4u) {
            MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = MarykTypeEnum
                )
            )
        }

        val mapWithList by wrap(5u) {
            MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = ListDefinition(
                    valueDefinition = StringDefinition()
                )
            )
        }

        val mapWithSet by wrap(6u) {
            MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = SetDefinition(
                    valueDefinition = StringDefinition()
                )
            )
        }

        val mapWithMap by wrap(7u) {
            MapDefinition(
                required = false,
                keyDefinition = StringDefinition(),
                valueDefinition = MapDefinition(
                    keyDefinition = StringDefinition(),
                    valueDefinition = StringDefinition()
                )
            )
        }

        val incMap by wrap(8u) {
            IncrementingMapDefinition(
                required = false,
                keyNumberDescriptor = UInt32,
                valueDefinition = EmbeddedValuesDefinition(
                    dataModel = { EmbeddedMarykModel }
                )
            )
        }
    }

    operator fun invoke(
        multi: TypedValue<MarykTypeEnum<*>, Any>? = null,
        mapStringString: Map<String, String>? = null,
        mapIntObject: Map<UInt, Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>? = null,
        mapIntMulti: Map<UInt, TypedValue<MarykTypeEnum<*>, Any>>? = null,
        mapWithList: Map<String, List<String>>? = null,
        mapWithSet: Map<String, Set<String>>? = null,
        mapWithMap: Map<String, Map<String, String>>? = null,
        incMap: Map<UInt, Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>? = null
    ) = this.values {
        mapNonNulls(
            this.multi with multi,
            this.mapStringString with mapStringString,
            this.mapIntObject with mapIntObject,
            this.mapIntMulti with mapIntMulti,
            this.mapWithList with mapWithList,
            this.mapWithSet with mapWithSet,
            this.mapWithMap with mapWithMap,
            this.incMap with incMap
        )
    }
}
