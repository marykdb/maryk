package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Changes for a set property of [T] referred by [reference] with [addValues] and [deleteValues] */
data class SetValueChanges<T: Any> internal constructor(
    override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>,
    val addValues: Set<T>? = null,
    val deleteValues: Set<T>? = null
) : DefinedByReference<Set<T>> {
    internal object Properties : PropertyDefinitions<SetValueChanges<*>>() {
        val reference = DefinedByReference.addReference(this, SetValueChanges<*>::reference)

        init {
            add(1, "addValues", SetDefinition(
                required = false,
                valueDefinition = valueDefinition
            ), SetValueChanges<*>::addValues)

            add(2, "deleteValues", SetDefinition(
                required = false,
                valueDefinition = valueDefinition
            ), SetValueChanges<*>::deleteValues)
        }
    }

    internal companion object: QueryDataModel<SetValueChanges<out Any>, Properties>(
        properties = Properties
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: DataObjectMap<SetValueChanges<out Any>>) = SetValueChanges<Any>(
            reference = map(0),
            addValues = map(1),
            deleteValues = map(2)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(
    contextualResolver = { context: DataModelPropertyContext? ->
        (context?.reference as SetReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
    }
)

/**
 * Convenience infix method to define an map value change
 * Set property of values [T] with [addValues] and [deleteValues] for changes
 */
fun <T: Any> IsPropertyReference<Set<T>, IsCollectionDefinition<T, Set<T>, *, *>>.change(
    addValues: Set<T>? = null,
    deleteValues: Set<T>? = null
) =
    SetValueChanges(
        reference = this,
        addValues = addValues,
        deleteValues = deleteValues
    )
