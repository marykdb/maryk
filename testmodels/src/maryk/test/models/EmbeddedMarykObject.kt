package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.string
import maryk.core.values.ObjectValues

data class EmbeddedMarykObject(
    val value: String,
    val model: EmbeddedMarykObject? = null,
    val marykModel: TestMarykObject? = null
) {
    companion object : ObjectDataModel<EmbeddedMarykObject, Companion>(EmbeddedMarykObject::class) {
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

        override fun invoke(values: ObjectValues<EmbeddedMarykObject, Companion>) =
            EmbeddedMarykObject(
                value = values(value.index),
                model = values(model.index),
                marykModel = values(marykModel.index)
            )

        override fun equals(other: Any?) =
            other is ObjectDataModel<*, *> &&
                this.Meta.name == other.Meta.name &&
                this.size == other.size
    }
}
