package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object SimpleMarykModel : RootDataModel<SimpleMarykModel, SimpleMarykModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value by define(1u) {
            StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        }
    }

    operator fun invoke(
        value: String = "haha"
    ) = values {
        mapNonNulls(
            this.value with value
        )
    }
}
