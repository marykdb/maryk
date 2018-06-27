package maryk.core.query.pairs

import maryk.core.models.SimpleDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.ValueRange

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceValueRangePair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val range: ValueRange<T>
) : DefinedByReference<T> {
    internal object Properties: PropertyDefinitions<ReferenceValueRangePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueRangePair<*>::reference
        )
        val range = add(
            index = 1, name = "range",
            definition = EmbeddedObjectDefinition(
                dataModel = { ValueRange }
            ),
            getter = ReferenceValueRangePair<*>::range
        )
    }

    internal companion object: SimpleDataModel<ReferenceValueRangePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: DataObjectMap<ReferenceValueRangePair<*>>) = ReferenceValueRangePair<Any>(
            reference = map(0),
            range = map(1)
        )
    }
}

/** Convenience infix method to create Reference [range] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(range: ValueRange<T>) =
    ReferenceValueRangePair(this, range)

/** Creates a reference value [range] pair */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(
    range: ClosedRange<T>
) = ReferenceValueRangePair(this, ValueRange(range.start, range.endInclusive, true, true))
