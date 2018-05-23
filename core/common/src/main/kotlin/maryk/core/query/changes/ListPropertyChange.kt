package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/**
 * Changes for a list property containing values of type [T]
 * Options are to [addValuesToEnd], [addValuesAtIndex], [deleteValues] and/or [deleteAtIndex]
 * Optionally compares against [valueToCompare] and will only change value if values match
 */
fun <T:Any> IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>.change(
    addValuesToEnd: List<T>? = null,
    addValuesAtIndex: Map<Int, T>? = null,
    deleteValues: List<T>? = null,
    deleteAtIndex: Set<Int>? = null,
    valueToCompare: List<T>? = null
) = ListPropertyChange(this, addValuesToEnd, addValuesAtIndex, deleteValues, deleteAtIndex, valueToCompare)

/**
 * Changes for a list property containing values of type [T] referred by [reference]
 * Options are to [addValuesToEnd], [addValuesAtIndex], [deleteValues] and/or [deleteAtIndex]
 * Optionally compares against [valueToCompare] and will only change value if values match
 */
data class ListPropertyChange<T: Any> internal constructor(
    override val reference: IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>,
    val addValuesToEnd: List<T>? = null,
    val addValuesAtIndex: Map<Int, T>? = null,
    val deleteValues: List<T>? = null,
    val deleteAtIndex: Set<Int>? = null,
    override val valueToCompare: List<T>? = null
) : IsPropertyOperation<List<T>> {
    override val changeType = ChangeType.ListChange

    internal companion object: QueryDataModel<ListPropertyChange<*>>(
        properties = object : PropertyDefinitions<ListPropertyChange<*>>() {
            init {
                DefinedByReference.addReference(this, ListPropertyChange<*>::reference)

                @Suppress("UNCHECKED_CAST")
                add(1, "valueToCompare", ContextualCollectionDefinition(
                    required = false,
                    contextualResolver = { context: DataModelPropertyContext? ->
                        (context?.reference as ListReference<Any, IsPropertyContext>?)
                            ?.propertyDefinition as IsByteTransportableCollection<Any, Collection<Any>, DataModelPropertyContext>?
                                ?: throw ContextNotFoundException()
                    }
                ), ListPropertyChange<*>::valueToCompare)

                add(2, "addValuesToEnd", valueListDefinition, ListPropertyChange<*>::addValuesToEnd)

                add(3, "addValuesAtIndex", MapDefinition(
                    required = false,
                    keyDefinition = NumberDefinition(type = SInt32),
                    valueDefinition = valueDefinition
                ), ListPropertyChange<*>::addValuesAtIndex)

                add(4, "deleteValues", valueListDefinition, ListPropertyChange<*>::deleteValues)

                add(5, "deleteAtIndex", SetDefinition(
                    required = false,
                    valueDefinition = NumberDefinition(type = SInt32)
                ), ListPropertyChange<*>::deleteAtIndex)
            }
        }
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: Map<Int, *>) = ListPropertyChange<Any>(
            reference = map(0),
            valueToCompare = map(1),
            addValuesToEnd = map(2),
            addValuesAtIndex = map(3),
            deleteValues = map(4),
            deleteAtIndex = map(5)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
    (context?.reference as ListReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
})

private val valueListDefinition = ListDefinition(
    required = false,
    valueDefinition = valueDefinition
)
