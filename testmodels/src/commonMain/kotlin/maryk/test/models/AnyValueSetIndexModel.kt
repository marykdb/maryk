package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string

object AnyValueSetIndexModel : RootDataModel<AnyValueSetIndexModel>(
    indexes = {
        listOf(
            AnyValueSetIndexModel { setValues.refToAny() }
        )
    }
) {
    val name by string(index = 1u)

    val setValues by set(
        index = 2u,
        required = false,
        valueDefinition = StringDefinition(maxSize = 20u)
    )
}
