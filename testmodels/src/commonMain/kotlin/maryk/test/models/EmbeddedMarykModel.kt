package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.values.Values

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

    operator fun invoke(
        value: String,
        model: Values<EmbeddedMarykModel>? = null,
        marykModel: Values<TestMarykModel>? = null
    ) = create {
        this.value += value
        this.model += model
        this.marykModel += marykModel
    }
}
