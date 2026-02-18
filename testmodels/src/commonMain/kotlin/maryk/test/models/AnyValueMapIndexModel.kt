package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string

object AnyValueMapIndexModel : RootDataModel<AnyValueMapIndexModel>(
    indexes = {
        listOf(
            AnyValueMapIndexModel { mapValues.refToAnyKey() }
        )
    }
) {
    val name by string(index = 1u)

    val mapValues by map(
        index = 2u,
        required = false,
        keyDefinition = StringDefinition(maxSize = 20u),
        valueDefinition = StringDefinition(maxSize = 20u)
    )
}
