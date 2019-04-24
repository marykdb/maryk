package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.values.Values

object EmbeddedMarykModel : DataModel<EmbeddedMarykModel, EmbeddedMarykModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value = add(
            index = 1u, name = "value",
            definition = StringDefinition()
        )
        val model = add(
            index = 2u, name = "model",
            definition = EmbeddedValuesDefinition(
                required = false,
                dataModel = { EmbeddedMarykModel }
            )
        )
        val marykModel = add(
            index = 3u, name = "marykModel",
            definition = EmbeddedValuesDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
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
