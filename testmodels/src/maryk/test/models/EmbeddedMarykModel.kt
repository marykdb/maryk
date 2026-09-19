package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string

object EmbeddedMarykModel : DataModel<EmbeddedMarykModel>(
    reservedIndices = listOf(999u),
    reservedNames = listOf("reserved"),
) {
    val value by string(
        index = 1u
    )
    val model by embed(
        index = 2u,
        required = false,
        dataModel = { EmbeddedMarykModel }
    )
    val marykModel by embed(
        index = 3u,
        required = false,
        dataModel = { TestMarykModel }
    )
}
