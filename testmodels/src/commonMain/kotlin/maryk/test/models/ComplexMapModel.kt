@file:Suppress("EXPERIMENTAL_API_USAGE")

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
import maryk.test.models.ComplexMapModel.Properties
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3

object ComplexMapModel: RootDataModel<ComplexMapModel, Properties>(
    name = "ComplexMapModel",
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val stringString = add(
            index = 1, name = "stringString",
            definition = MapDefinition(
                required = false,
                keyDefinition = StringDefinition(
                    minSize = 1,
                    maxSize = 20
                ),
                valueDefinition = StringDefinition(
                    maxSize = 500
                )
            )
        )

        val intObject = add(
            index = 2, name = "intObject",
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
        val intMulti = add(
            index = 3, name = "intMulti",
            definition = MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = MultiTypeDefinition<Option, IsPropertyContext>(
                    typeEnum = Option,
                    definitionMap = mapOf(
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
        stringString: Map<String,String>? = null,
        intObject: Map<UInt, Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>? = null,
        intMulti: Map<UInt, TypedValue<Option, *>>? = null
    ) = this.values {
        mapNonNulls(
            this.stringString with stringString,
            this.intObject with intObject,
            this.intMulti with intMulti
        )
    }
}
