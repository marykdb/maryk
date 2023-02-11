package maryk.test.models

import maryk.core.properties.RootModel
import maryk.core.properties.definitions.string

object SimpleMarykModel : RootModel<SimpleMarykModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
