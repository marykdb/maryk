package maryk.core.query.pairs

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

data class NamedRegexPair internal constructor(
    val name: String,
    val regex: Regex
) {
    override fun toString() = "$name: ${regex.pattern}"

    override fun equals(other: Any?): Boolean {
        if (other !is NamedRegexPair) return false
        return this.name == other.name && this.regex.pattern == other.regex.pattern
    }

    override fun hashCode() = 31 * name.hashCode() + regex.pattern.hashCode()

    companion object : TypedObjectDataModel<NamedRegexPair, Companion, RequestContext, RequestContext>() {
        val name by string(
            index = 1u,
            getter = NamedRegexPair::name
        )

        val regex by string(
            index = 2u,
            getter = NamedRegexPair::regex,
            fromSerializable = { value -> value?.let(::Regex) },
            toSerializable = { value: Regex?, _: RequestContext? -> value?.pattern }
        )

        override fun invoke(values: ObjectValues<NamedRegexPair, Companion>) = NamedRegexPair(
            name = values(1u),
            regex = values(2u)
        )
    }
}

infix fun String.with(regex: Regex) = NamedRegexPair(this, regex)
