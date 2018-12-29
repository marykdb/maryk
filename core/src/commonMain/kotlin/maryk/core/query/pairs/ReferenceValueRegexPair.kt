package maryk.core.query.pairs

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [regex] */
data class ReferenceValueRegexPair internal constructor(
    override val reference: IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>, *>,
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

    object Properties: ObjectPropertyDefinitions<ReferenceValueRegexPair>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueRegexPair::reference
        )
        @Suppress("UNCHECKED_CAST")
        val regex = add(
            index = 2, name = "regex",
            definition = StringDefinition(),
            fromSerializable = { value: String? ->
                value?.let { Regex(value) }
            },
            toSerializable = { value: Regex?, _ ->
                value?.pattern
            },
            getter = ReferenceValueRegexPair::regex
        )
    }

    companion object: SimpleObjectDataModel<ReferenceValueRegexPair, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValueRegexPair, Properties>) = ReferenceValueRegexPair(
            reference = values(1),
            regex = values(2)
        )
    }
}

/** Convenience infix method to create Reference [regex] pairs */
infix fun IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>, *>.with(regex: Regex) =
    ReferenceValueRegexPair(this, regex)
