package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.string
import maryk.core.values.ObjectValues

@Suppress("unused")
data class EmbeddedMarykObject(
    val value: String,
    val model: EmbeddedMarykObject? = null,
    val marykModel: TestMarykObject? = null
) {
    object Properties : ObjectPropertyDefinitions<EmbeddedMarykObject>() {
        val value by string(
            index = 1u,
            getter = EmbeddedMarykObject::value
        )
        val model by embedObject(
            index = 2u,
            getter = EmbeddedMarykObject::model,
            dataModel = { EmbeddedMarykObject },
            required = false
        )
        val marykModel by embedObject(
            index = 3u,
            getter = EmbeddedMarykObject::marykModel,
            dataModel = { TestMarykObject },
            required = false
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
