package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/**
 * Changes for a list property containing values of type [T] referred by [reference]
 * Options are to [addValuesToEnd], [addValuesAtIndex], [deleteValues] and/or [deleteAtIndex]
 */
data class ListChange<T: Any> internal constructor(
    override val reference: IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>,
    val addValuesToEnd: List<T>? = null,
    val addValuesAtIndex: Map<Int, T>? = null,
    val deleteValues: List<T>? = null,
    val deleteAtIndex: Set<Int>? = null
) : IsPropertyOperation<List<T>> {
    override val changeType = ChangeType.ListChange

    internal companion object: QueryDataModel<ListChange<*>>(
        properties = object : PropertyDefinitions<ListChange<*>>() {
            init {
                DefinedByReference.addReference(this, ListChange<*>::reference)

                add(1, "addValuesToEnd", valueListDefinition, ListChange<*>::addValuesToEnd)

                add(2, "addValuesAtIndex", MapDefinition(
                    required = false,
                    keyDefinition = NumberDefinition(type = SInt32),
                    valueDefinition = valueDefinition
                ), ListChange<*>::addValuesAtIndex)

                add(3, "deleteValues", valueListDefinition, ListChange<*>::deleteValues)

                add(4, "deleteAtIndex", SetDefinition(
                    required = false,
                    valueDefinition = NumberDefinition(type = SInt32)
                ), ListChange<*>::deleteAtIndex)
            }
        }
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: Map<Int, *>) = ListChange<Any>(
            reference = map(0),
            addValuesToEnd = map(1),
            addValuesAtIndex = map(2),
            deleteValues = map(3),
            deleteAtIndex = map(4)
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
