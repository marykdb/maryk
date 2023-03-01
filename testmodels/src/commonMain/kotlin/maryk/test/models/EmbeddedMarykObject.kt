package maryk.test.models

import maryk.core.properties.ObjectModel
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.string
import maryk.core.values.ObjectValues

@Suppress("unused")
data class EmbeddedMarykObject(
    val value: String,
    val model: EmbeddedMarykObject? = null,
    val marykModel: TestMarykObject? = null
) {
    companion object : ObjectModel<EmbeddedMarykObject, Companion>(EmbeddedMarykObject::class) {
        val value by string(
            index = 1u,
            getter = EmbeddedMarykObject::value
        )
        val model by embedObject(
            index = 2u,
            getter = EmbeddedMarykObject::model,
            dataModel = { EmbeddedMarykObject.Model },
            required = false
        )
        val marykModel by embedObject(
            index = 3u,
            getter = EmbeddedMarykObject::marykModel,
            dataModel = { TestMarykObject.Model },
            required = false
        )

        override fun invoke(values: ObjectValues<EmbeddedMarykObject, Companion>) =
            EmbeddedMarykObject(
                value = values(1u),
                model = values(2u),
                marykModel = values(3u)
            )

        override fun equals(other: Any?) =
            other is ObjectModel<*, *> &&
                this.Model.name == other.Model.name &&
                this.size == other.size
    }
}
