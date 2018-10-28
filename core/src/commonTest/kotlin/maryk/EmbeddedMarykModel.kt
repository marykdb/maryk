package maryk

import maryk.core.models.DataModel
import maryk.core.objects.Values
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition

object EmbeddedMarykModel: DataModel<EmbeddedMarykModel, EmbeddedMarykModel.Properties>(
    name = "EmbeddedMarykModel",
    properties = EmbeddedMarykModel.Properties
) {
    object Properties : PropertyDefinitions() {
        val value = add(
            index = 1, name = "value",
            definition = StringDefinition()
        )
        val model = add(
            index = 2, name = "model",
            definition = EmbeddedValuesDefinition(
                required = false,
                dataModel = { EmbeddedMarykModel }
            )
        )
        val marykModel = add(
            index = 3, name = "marykModel",
            definition = EmbeddedValuesDefinition(
                required = false,
                dataModel = { TestMarykModel }
            )
        )
    }

    operator fun invoke(
        value: String,
        model: Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>? = null,
        marykModel: Values<TestMarykModel, TestMarykModel.Properties>? = null
    ) = this.map {
        mapNonNulls(
            this.value with value,
            this.model with model,
            this.marykModel with marykModel
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DataModel<*, *>) return false

        @Suppress("UNCHECKED_CAST")
        val otherModel = other as DataModel<*, PropertyDefinitions>

        if (this.name != otherModel.name) return false
        if (this.properties.size != otherModel.properties.size) return false

        return true
    }
}
