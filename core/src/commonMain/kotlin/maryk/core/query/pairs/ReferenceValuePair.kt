package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Compares given [value] of type [T] against referenced value [reference] */
data class ReferenceValuePair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    val value: T
) : DefinedByReference<T> {

    override fun toString() = "$reference: $value"

    object Properties: ObjectPropertyDefinitions<ReferenceValuePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValuePair<*>::reference
        )
        val value = add(
            2, "value",
            ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            ),
            ReferenceValuePair<*>::value
        )
    }

    companion object: SimpleObjectDataModel<ReferenceValuePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceValuePair<*>, Properties>) = ReferenceValuePair(
            reference = values(1),
            value = values(2)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <T: Any> IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>.with(value: T) =
    ReferenceValuePair(this, value)
