package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.DataModelPropertyContext

/** Changes for a list property
 * @param reference to property affected by the change
 * @param addValuesToEnd values to add at the end of the list
 * @param addValuesAtIndex values to add at the index positions
 * @param deleteValues values to delete from list (Will delete all occurrences)
 * @param deleteAtIndex indexes to delete values at
 * @param valueToCompare (optional) if List the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
data class ListPropertyChange<T: Any>(
        override val reference: ListReference<T, IsPropertyContext>,
        val addValuesToEnd: List<T>? = null,
        val addValuesAtIndex: Map<Int, T>? = null,
        val deleteValues: List<T>? = null,
        val deleteAtIndex: List<Int>? = null,
        override val valueToCompare: List<T>? = null
) : IsPropertyOperation<List<T>> {
    override val changeType = ChangeType.LIST_CHANGE

    object Properties {
        @Suppress("UNCHECKED_CAST")
        private val valueDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
            (context!!.reference!! as ListReference<Any, IsPropertyContext>).propertyDefinition.valueDefinition
        })
        @Suppress("UNCHECKED_CAST")
        val valueToCompare = ContextualCollectionDefinition(
                name = "valueToCompare",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    (context!!.reference!! as ListReference<Any, IsPropertyContext>).propertyDefinition as IsByteTransportableCollection<Any, Collection<Any>, DataModelPropertyContext>
                }
        )
        val addValuesToEnd = ListDefinition(
                name = "addValuesToEnd",
                index = 2,
                valueDefinition = valueDefinition
        )
        val addValuesAtIndex = MapDefinition(
                name = "addValuesAtIndex",
                index = 3,
                keyDefinition = NumberDefinition(required = true, type = SInt32),
                valueDefinition = valueDefinition
        )
        val deleteValues = ListDefinition(
                name = "deleteValues",
                index = 4,
                valueDefinition = valueDefinition
        )
        val deleteAtIndex = ListDefinition(
                name = "deleteAtIndex",
                index = 5,
                valueDefinition = NumberDefinition(required = true, type = SInt32)
        )
    }

    companion object: QueryDataModel<ListPropertyChange<*>>(
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, ListPropertyChange<*>::reference),
                    Def(Properties.valueToCompare, ListPropertyChange<*>::valueToCompare),
                    Def(Properties.addValuesToEnd, ListPropertyChange<*>::addValuesToEnd),
                    Def(Properties.addValuesAtIndex, ListPropertyChange<*>::addValuesAtIndex),
                    Def(Properties.deleteValues, ListPropertyChange<*>::deleteValues),
                    Def(Properties.deleteAtIndex, ListPropertyChange<*>::deleteAtIndex)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ListPropertyChange(
                reference = map[0] as ListReference<Any, IsPropertyContext>,
                valueToCompare = map[1] as List<Any>?,
                addValuesToEnd = map[2] as List<Any>?,
                addValuesAtIndex = map[3] as Map<Int, Any>?,
                deleteValues = map[4] as List<Any>?,
                deleteAtIndex = map[5] as List<Int>?
        )
    }
}