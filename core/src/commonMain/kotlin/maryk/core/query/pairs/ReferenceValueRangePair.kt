package maryk.core.query.pairs

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.ValueRange
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceValueRangePair<T : Comparable<T>> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val range: ValueRange<T>
) : DefinedByReference<T> {

    override fun toString() = "$reference: $range"

    object Properties : ReferenceValuePairPropertyDefinitions<ReferenceValueRangePair<*>, ValueRange<*>, ValueRange<*>>() {
        override val reference = DefinedByReference.addReference(
            this,
            ReferenceValueRangePair<*>::reference
        )
        override val value = add(
            index = 2u, name = "range",
            definition = EmbeddedObjectDefinition(
                dataModel = { ValueRange }
            ),
            getter = ReferenceValueRangePair<*>::range
        )
    }

    companion object : SimpleObjectDataModel<ReferenceValueRangePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValueRangePair<*>, Properties>) =
            ReferenceValueRangePair<Comparable<Any>>(
                reference = values(1u),
                range = values(2u)
            )
    }
}

/** Convenience infix method to create Reference [range] pairs */
infix fun <T : Comparable<T>> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(range: ValueRange<T>) =
    ReferenceValueRangePair(this, range)

/** Creates a reference value [range] pair */
infix fun <T : Comparable<T>> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(
    range: ClosedRange<T>
) = ReferenceValueRangePair(this, ValueRange(range.start, range.endInclusive, inclusiveFrom = true, inclusiveTo = true))
