package maryk.core.query.pairs

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceValueRangePair<T: Comparable<T>> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val range: ValueRange<T>
) : DefinedByReference<T> {

    override fun toString() = "$reference: $range"

    object Properties: ReferenceValuePairPropertyDefinitions<ReferenceValueRangePair<*>, ValueRange<*>>() {
        override val reference = DefinedByReference.addReference(
            this,
            ReferenceValueRangePair<*>::reference
        )
        @Suppress("UNCHECKED_CAST")
        override val value = add(
            index = 2, name = "range",
            definition = EmbeddedObjectDefinition(
                dataModel = { ValueRange }
            ),
            getter = ReferenceValueRangePair<*>::range
        ) as IsPropertyDefinitionWrapper<Any, ValueRange<*>, RequestContext, ReferenceValueRangePair<*>>
    }

    companion object: SimpleObjectDataModel<ReferenceValueRangePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValueRangePair<*>, Properties>) = ReferenceValueRangePair<Comparable<Any>>(
            reference = values(1),
            range = values(2)
        )
    }
}

/** Convenience infix method to create Reference [range] pairs */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(range: ValueRange<T>) =
    ReferenceValueRangePair(this, range)

/** Creates a reference value [range] pair */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(
    range: ClosedRange<T>
) = ReferenceValueRangePair(this, ValueRange(range.start, range.endInclusive, inclusiveFrom = true, inclusiveTo = true))
