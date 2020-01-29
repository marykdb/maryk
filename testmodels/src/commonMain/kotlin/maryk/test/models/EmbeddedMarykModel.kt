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
        val value by define(1u) {
            StringDefinition()
        }
        val model by define(2u) {
            EmbeddedValuesDefinition(
                required = false,
                dataModel = { EmbeddedMarykModel }
            )
        }
        val marykModel by define(3u) {
            EmbeddedValuesDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        }
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
