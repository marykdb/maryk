package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Changes for a set property of [T] referred by [reference] with [addValues] and [deleteValues] */
data class SetChange<T: Any> internal constructor(
    override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>,
    val addValues: Set<T>? = null,
    val deleteValues: Set<T>? = null
) : IsPropertyOperation<Set<T>> {
    override val changeType = ChangeType.SetChange

    internal companion object: QueryDataModel<SetChange<out Any>>(
        properties = object : PropertyDefinitions<SetChange<*>>() {
            init {
                DefinedByReference.addReference(this, SetChange<*>::reference)

                add(1, "addValues", SetDefinition(
                    required = false,
                    valueDefinition = valueDefinition
                ), SetChange<*>::addValues)

                add(2, "deleteValues", SetDefinition(
                    required = false,
                    valueDefinition = valueDefinition
                ), SetChange<*>::deleteValues)
            }
        }
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: Map<Int, *>) = SetChange<Any>(
            reference = map(0),
            addValues = map(1),
            deleteValues = map(2)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
    (context?.reference as SetReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
})
