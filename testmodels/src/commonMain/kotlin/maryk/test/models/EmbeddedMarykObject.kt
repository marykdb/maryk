package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.values.ObjectValues

@Suppress("unused")
data class EmbeddedMarykObject(
    val value: String,
    val model: EmbeddedMarykObject? = null,
    val marykModel: TestMarykObject? = null
) {
    object Properties : ObjectPropertyDefinitions<EmbeddedMarykObject>() {
        val value = add(
            index = 1u, name = "value",
            definition = StringDefinition(),
            getter = EmbeddedMarykObject::value
        )
        val model = add(
            index = 2u, name = "model",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { EmbeddedMarykObject }
            ),
            getter = EmbeddedMarykObject::model
        )
        val marykModel = add(
            index = 3u, name = "marykModel",
            definition = EmbeddedObjectDefinition(
                required = false,
                dataModel = { TestMarykObject }
            ),
            getter = EmbeddedMarykObject::marykModel
        )
    }

    companion object : ObjectDataModel<EmbeddedMarykObject, Properties>(
        name = "EmbeddedMarykObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<EmbeddedMarykObject, Properties>) =
            EmbeddedMarykObject(
                value = values(1u),
                model = values(2u),
                marykModel = values(3u)
            )

        override fun equals(other: Any?) =
            other is ObjectDataModel<*, *> &&
                this.name == other.name &&
                this.properties.size == other.properties.size
    }
}
