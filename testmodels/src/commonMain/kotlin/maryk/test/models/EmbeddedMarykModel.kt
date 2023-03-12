package maryk.test.models

import maryk.core.properties.Model
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.values.Values

object EmbeddedMarykModel : Model<EmbeddedMarykModel>(
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
    ) =
        create(
            this.value with value,
            this.model with model,
            this.marykModel with marykModel
        )
}
