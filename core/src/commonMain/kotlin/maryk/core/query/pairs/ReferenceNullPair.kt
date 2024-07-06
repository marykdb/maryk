package maryk.core.query.pairs

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.references.IsPropertyReference

/** Compares given [value] of type [T] against referenced value [reference] */
data class ReferenceNullPair<T : Any> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
) : IsReferenceValueOrNullPair<T> {
    override val value: T? = null

    override fun toString() = "$reference: $value"
}
