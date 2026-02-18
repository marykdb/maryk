package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32

object AnyValueIncMapIndexModel : RootDataModel<AnyValueIncMapIndexModel>(
    indexes = {
        listOf(
            AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }
        )
    }
) {
    val name by string(index = 1u)

    val incMapValues by incrementingMap(
        index = 2u,
        required = false,
        keyNumberDescriptor = UInt32,
        valueDefinition = StringDefinition(maxSize = 20u)
    )
}
