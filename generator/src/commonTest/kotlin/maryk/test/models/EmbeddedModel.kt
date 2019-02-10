package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object EmbeddedModel : DataModel<EmbeddedModel, EmbeddedModel.Properties>(
    name = "EmbeddedModel",
    properties = Properties
) {
    object Properties: PropertyDefinitions() {
        val value = add(
            index = 1, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        )
    }

    operator fun invoke(
        value: String = "haha"
    ) = values {
        mapNonNulls(
            this.value with value
        )
    }
}
