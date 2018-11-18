package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object SimpleMarykModel: RootDataModel<SimpleMarykModel, SimpleMarykModel.Properties>(
    name = "SimpleMarykModel",
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
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
    ) = this.values {
        mapNonNulls(
            this.value with value
        )
    }
}
