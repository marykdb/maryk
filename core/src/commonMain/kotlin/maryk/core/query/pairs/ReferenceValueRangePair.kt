package maryk.core.query.pairs

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceValueRangePair<T : Comparable<T>> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val range: ValueRange<T>
) : DefinedByReference<T> {

    override fun toString() = "$reference: $range"

    object Properties : ReferenceValuePairPropertyDefinitions<ReferenceValueRangePair<*>, ValueRange<*>, ValueRange<*>, EmbeddedObjectDefinitionWrapper<ValueRange<*>, ValueRange<*>, ValueRange.Properties, ValueRange.Companion, RequestContext, RequestContext, ReferenceValueRangePair<*>>>() {
        override val reference by addReference(
            ReferenceValueRangePair<*>::reference
        )
        override val value by embedObject(
            index = 2u,
            getter = ReferenceValueRangePair<*>::range,
            dataModel = { ValueRange }
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
