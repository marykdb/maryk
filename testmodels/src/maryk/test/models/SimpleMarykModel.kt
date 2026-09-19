package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string

object SimpleMarykModel : RootDataModel<SimpleMarykModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
