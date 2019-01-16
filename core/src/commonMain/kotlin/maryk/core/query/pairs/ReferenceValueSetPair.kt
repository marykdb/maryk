package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Compares given [values] set of type [T] against referenced value [reference] */
data class ReferenceValueSetPair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val values: Set<T>
) : DefinedByReference<T> {
    override fun toString() = "$reference: $values]"

    object Properties: ObjectPropertyDefinitions<ReferenceValueSetPair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueSetPair<*>::reference
        )
        val values = add(2, "values", SetDefinition(
            valueDefinition = ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        ), ReferenceValueSetPair<*>::values)
    }

    companion object: SimpleObjectDataModel<ReferenceValueSetPair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValueSetPair<*>, Properties>) = ReferenceValueSetPair(
            reference = values(1),
            values = values(2)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(value: Set<T>) =
    ReferenceValueSetPair(this, value)
