package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.definitions.string

object EmbeddedModel : DataModel<EmbeddedModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
