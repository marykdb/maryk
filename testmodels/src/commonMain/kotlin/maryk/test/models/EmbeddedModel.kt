package maryk.test.models

import maryk.core.properties.Model
import maryk.core.properties.definitions.string

object EmbeddedModel : Model<EmbeddedModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
