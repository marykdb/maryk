package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DataModelPropertyContext

/**
 * Changes for a set property of [T] referred with [addValues] and [deleteValues]
 * If [valueToCompare] is defined operation will only complete if they both are equal
 */
fun <T:Any> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>.change(
    addValues: Set<T>? = null,
    deleteValues: Set<T>? = null,
    valueToCompare: Set<T>? = null
) = SetPropertyChange(this, addValues, deleteValues, valueToCompare)

/**
 * Changes for a set property of [T] referred by [reference] with [addValues] and [deleteValues]
 * If [valueToCompare] is defined operation will only complete if they both are equal
 */
data class SetPropertyChange<T: Any> internal constructor(
    override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>,
    val addValues: Set<T>? = null,
    val deleteValues: Set<T>? = null,
    override val valueToCompare: Set<T>? = null
) : IsPropertyOperation<Set<T>> {
    override val changeType = ChangeType.SetChange

    internal companion object: QueryDataModel<SetPropertyChange<out Any>>(
        properties = object : PropertyDefinitions<SetPropertyChange<*>>() {
            init {
                IsPropertyOperation.addReference(this, SetPropertyChange<*>::reference)
                add(1, "valueToCompare", ContextualCollectionDefinition(
                    required = false,
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context?.reference?.propertyDefinition?.definition as IsByteTransportableCollection<Any, Collection<Any>, DataModelPropertyContext>?
                                ?: throw ContextNotFoundException()
                    }
                ), SetPropertyChange<*>::valueToCompare)

                add(2, "addValues", SetDefinition(
                    required = false,
                    valueDefinition = valueDefinition
                ), SetPropertyChange<*>::addValues)

                add(3, "deleteValues", SetDefinition(
                    required = false,
                    valueDefinition = valueDefinition
                ), SetPropertyChange<*>::deleteValues)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = SetPropertyChange(
            reference = map[0] as IsPropertyReference<Set<Any>, IsValueDefinition<Set<Any>, IsPropertyContext>>,
            valueToCompare = map[1] as Set<Any>?,
            addValues = map[2] as Set<Any>?,
            deleteValues = map[3] as Set<Any>?
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
    (context?.reference as SetReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
})
