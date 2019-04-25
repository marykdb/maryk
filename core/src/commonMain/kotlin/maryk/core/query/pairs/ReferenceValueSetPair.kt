package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Compares given [values] set of type [T] against referenced value [reference] */
data class ReferenceValueSetPair<T : Any> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val values: Set<T>
) : DefinedByReference<T> {
    override fun toString() = "$reference: $values]"

    object Properties : ReferenceValuePairPropertyDefinitions<ReferenceValueSetPair<*>, Set<*>, Set<*>>() {
        override val reference = DefinedByReference.addReference(
            this,
            ReferenceValueSetPair<*>::reference
        )
        @Suppress("UNCHECKED_CAST")
        override val value = add(
            2u, "values",
            SetDefinition(
                valueDefinition = ContextualValueDefinition(
                    contextualResolver = { context: RequestContext? ->
                        context?.reference?.let {
                            it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                        } ?: throw ContextNotFoundException()
                    }
                )
            ),
            ReferenceValueSetPair<*>::values as (ReferenceValueSetPair<*>) -> Set<Any>?
        ) as IsPropertyDefinitionWrapper<Set<*>, Set<*>, RequestContext, ReferenceValueSetPair<*>>
    }

    companion object : SimpleObjectDataModel<ReferenceValueSetPair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValueSetPair<*>, Properties>) = ReferenceValueSetPair(
            reference = values(1u),
            values = values(2u)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T : Any> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(value: Set<T>) =
    ReferenceValueSetPair(this, value)
