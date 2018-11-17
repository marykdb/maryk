package maryk.core.query.pairs

import maryk.core.models.SimpleObjectDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.ValueRange

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceValueRangePair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>, *>,
    val range: ValueRange<T>
) : DefinedByReference<T> {
    object Properties: ObjectPropertyDefinitions<ReferenceValueRangePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueRangePair<*>::reference
        )
        val range = add(
            index = 2, name = "range",
            definition = EmbeddedObjectDefinition(
                dataModel = { ValueRange }
            ),
            getter = ReferenceValueRangePair<*>::range
        )
    }

    companion object: SimpleObjectDataModel<ReferenceValueRangePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ReferenceValueRangePair<*>, Properties>) = ReferenceValueRangePair<Any>(
            reference = map(1),
            range = map(2)
        )
    }
}

/** Convenience infix method to create Reference [range] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>, *>.with(range: ValueRange<T>) =
    ReferenceValueRangePair(this, range)

/** Creates a reference value [range] pair */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>, *>.with(
    range: ClosedRange<T>
) = ReferenceValueRangePair(this, ValueRange(range.start, range.endInclusive, true, true))
