package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values
import maryk.test.models.ComplexModel.Properties
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3

object ComplexModel : RootDataModel<ComplexModel, Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val multi = add(
            index = 1, name = "multi",
            definition = MultiTypeDefinition<Option, IsPropertyContext>(
                required = false,
                typeEnum = Option,
                typeIsFinal = false,
                definitionMap = mapOf(
                    V1 to StringDefinition(),
                    V2 to NumberDefinition(type = SInt32),
                    V3 to EmbeddedValuesDefinition(
                        dataModel = { EmbeddedMarykModel }
                    )
                )
            )
        )

        val mapStringString = add(
            index = 2, name = "mapStringString",
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
            index = 3, name = "mapIntObject",
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

        val mapIntMulti = add(
            index = 4, name = "mapIntMulti",
            definition = MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = Option,
                    definitionMap = definitionMap(
                        V1 to StringDefinition(),
                        V2 to NumberDefinition(type = SInt32),
                        V3 to EmbeddedValuesDefinition(
                            dataModel = { EmbeddedMarykModel }
                        )
                    )
                )
            )
        )
    }

    operator fun invoke(
        multi: TypedValue<Option, Any>? = null,
        mapStringString: Map<String, String>? = null,
        mapIntObject: Map<UInt, Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>? = null,
        mapIntMulti: Map<UInt, TypedValue<Option, Any>>? = null
    ) = this.values {
        mapNonNulls(
            this.multi with multi,
            this.mapStringString with mapStringString,
            this.mapIntObject with mapIntObject,
            this.mapIntMulti with mapIntMulti
        )
    }
}
