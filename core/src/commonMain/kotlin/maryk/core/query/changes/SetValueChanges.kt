package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext

/** Changes for a set property of [T] referred by [reference] with [addValues] and [deleteValues] */
data class SetValueChanges<T: Any> internal constructor(
    override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>,
    val addValues: Set<T>? = null,
    val deleteValues: Set<T>? = null
) : DefinedByReference<Set<T>> {
    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<SetValueChanges<*>>() {
        val reference = DefinedByReference.addReference(this, SetValueChanges<*>::reference)

        val addValues = add(2, "addValues", SetDefinition(
            required = false,
            valueDefinition = valueDefinition
        ), SetValueChanges<*>::addValues)

        val deleteValues = add(3, "deleteValues", SetDefinition(
            required = false,
            valueDefinition = valueDefinition
        ), SetValueChanges<*>::deleteValues)
    }

    companion object: QueryDataModel<SetValueChanges<out Any>, Properties>(
        properties = Properties
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: ObjectValues<SetValueChanges<out Any>, Properties>) = SetValueChanges<Any>(
            reference = map(1),
            addValues = map(2),
            deleteValues = map(3)
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
 * Convenience infix method to define an map value change
 * Set property of values [T] with [addValues] and [deleteValues] for changes
 */
fun <T: Any> IsPropertyReference<Set<T>, IsCollectionDefinition<T, Set<T>, *, *>, *>.change(
    addValues: Set<T>? = null,
    deleteValues: Set<T>? = null
) =
    SetValueChanges(
        reference = this,
        addValues = addValues,
        deleteValues = deleteValues
    )
