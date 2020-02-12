package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.values.Values

object EmbeddedMarykModel : DataModel<EmbeddedMarykModel, EmbeddedMarykModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value by string(1u)
        val model by embed(
            index = 2u,
            required = false,
            dataModel = { EmbeddedMarykModel }
        )
        val marykModel by embed(
            index = 3u,
            dataModel = { TestMarykModel },
            required = false
        )
    }

    operator fun invoke(
        value: String,
        model: Values<EmbeddedMarykModel, Properties>? = null,
        marykModel: Values<TestMarykModel, TestMarykModel.Properties>? = null
    ) = this.values {
        mapNonNulls(
            this.value with value,
            this.model with model,
            this.marykModel with marykModel
        )
    }

    override fun equals(other: Any?) =
        other is DataModel<*, *> &&
            this.name == other.name &&
            this.properties.size == other.properties.size
}
