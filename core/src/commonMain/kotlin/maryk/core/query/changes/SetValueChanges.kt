package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.set
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/**
 * Changes for a set property of [T] referred by [reference] with [addValues]
 */
data class SetValueChanges<T : Any> internal constructor(
    override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>,
    val addValues: Set<T>? = null
) : DefinedByReference<Set<T>> {
    companion object : QueryModel<SetValueChanges<out Any>, Companion>() {
        val reference by addReference(
            SetValueChanges<*>::reference
        )

        val addValues by set(
            index = 2u,
            getter = SetValueChanges<*>::addValues,
            required = false,
            valueDefinition = valueDefinition
        )

        override fun invoke(values: ObjectValues<SetValueChanges<out Any>, Companion>) = SetValueChanges<Any>(
            reference = values(1u),
            addValues = values(2u)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(
    contextualResolver = { context: RequestContext? ->
        (context?.reference as SetReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
    }
)

/**
 * Convenience infix method to define a set value change
 * Set property of values [T] with [addValues] for changes
 * First values are deleted before adding new
 */
fun <T : Any> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>.change(
    addValues: Set<T>? = null
) =
    SetValueChanges(
        reference = this,
        addValues = addValues
    )
