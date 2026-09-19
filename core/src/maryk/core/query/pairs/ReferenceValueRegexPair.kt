package maryk.core.query.pairs

import maryk.core.properties.IsPropertyContext
import maryk.core.models.ReferenceValuePairDataModel
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [regex] */
data class ReferenceValueRegexPair internal constructor(
    override val reference: IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>,
    val regex: Regex
) : DefinedByReference<String> {

    override fun toString() = "$reference: ${regex.pattern}"

    override fun equals(other: Any?): Boolean {
        if (other !is ReferenceValueRegexPair) return false
        if (this.reference != other.reference) return false
        if (this.regex.pattern != other.regex.pattern) return false
        return true
    }

    override fun hashCode(): Int {
        var result = reference.hashCode()
        result = 31 * result + regex.pattern.hashCode()
        return result
    }

    companion object : ReferenceValuePairDataModel<ReferenceValueRegexPair, Companion, String, Regex, FlexBytesDefinitionWrapper<String, Regex, IsPropertyContext, StringDefinition, ReferenceValueRegexPair>>() {
        override val reference by addReference(
            ReferenceValueRegexPair::reference
        )
        override val value by string(
            index = 2u,
            fromSerializable = { value: String? ->
                value?.let { Regex(value) }
            },
            toSerializable = { value: Regex?, _: IsPropertyContext? ->
                value?.pattern
            },
            shouldSerialize = { it is Regex },
            getter = ReferenceValueRegexPair::regex
        )

        override fun invoke(values: ObjectValues<ReferenceValueRegexPair, Companion>) = ReferenceValueRegexPair(
            reference = values(1u),
            regex = values(2u)
        )
    }
}

/** Convenience infix method to create Reference [regex] pairs */
infix fun IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>.with(regex: Regex) =
    ReferenceValueRegexPair(this, regex)
