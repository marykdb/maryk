package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
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
 * Changes for a list property containing values of type [T]
 * Options are to [addValuesToEnd], [addValuesAtIndex], [deleteValues] and/or [deleteAtIndex]
 */
data class ListValueChanges<T: Any> internal constructor(
    override val reference: IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>,
    val addValuesToEnd: List<T>? = null,
    val addValuesAtIndex: Map<Int, T>? = null,
    val deleteValues: List<T>? = null,
    val deleteAtIndex: Set<Int>? = null
) : DefinedByReference<List<T>> {
    internal object Properties : PropertyDefinitions<ListValueChanges<*>>() {
        val reference = DefinedByReference.addReference(this, ListValueChanges<*>::reference)

        val addValuesToEnd = add(1, "addValuesToEnd", valueListDefinition, ListValueChanges<*>::addValuesToEnd)

        val addValuesAtIndex = add(2, "addValuesAtIndex", MapDefinition(
            required = false,
            keyDefinition = NumberDefinition(type = SInt32),
            valueDefinition = valueDefinition
        ), ListValueChanges<*>::addValuesAtIndex)

        val deleteValues = add(3, "deleteValues", valueListDefinition, ListValueChanges<*>::deleteValues)

        val deleteAtIndex = add(4, "deleteAtIndex", SetDefinition(
            required = false,
            valueDefinition = NumberDefinition(type = SInt32)
        ), ListValueChanges<*>::deleteAtIndex)
    }

    internal companion object: QueryDataModel<ListValueChanges<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<ListValueChanges<*>>) = ListValueChanges<Any>(
            reference = map(0),
            addValuesToEnd = map(1),
            addValuesAtIndex = map(2),
            deleteValues = map(3),
            deleteAtIndex = map(4)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(
    contextualResolver = { context: DataModelPropertyContext? ->
        (context?.reference as ListReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
    }
)

private val valueListDefinition = ListDefinition(
    required = false,
    valueDefinition = valueDefinition
)

/**
 * Convenience infix method to define an array value change
 * Options are to [addValuesToEnd], [addValuesAtIndex], [deleteValues] and/or [deleteAtIndex]
 */
fun <T: Any> IsPropertyReference<List<T>, IsCollectionDefinition<T, List<T>, *, *>>.change(
    addValuesToEnd: List<T>? = null,
    addValuesAtIndex: Map<Int, T>? = null,
    deleteValues: List<T>? = null,
    deleteAtIndex: Set<Int>? = null
) =
    ListValueChanges(
        reference = this,
        addValuesToEnd = addValuesToEnd,
        addValuesAtIndex = addValuesAtIndex,
        deleteValues = deleteValues,
        deleteAtIndex = deleteAtIndex
    )
