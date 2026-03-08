package maryk.core.query.pairs

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

data class NamedValuePair internal constructor(
    val name: String,
    val value: String
) {
    override fun toString() = "$name: $value"

    companion object : TypedObjectDataModel<NamedValuePair, Companion, RequestContext, RequestContext>() {
        val name by string(
            index = 1u,
            getter = NamedValuePair::name
        )

        val value by string(
            index = 2u,
            getter = NamedValuePair::value
        )

        override fun invoke(values: ObjectValues<NamedValuePair, Companion>) = NamedValuePair(
            name = values(1u),
            value = values(2u)
        )
    }
}

infix fun String.with(value: String) = NamedValuePair(this, value)
