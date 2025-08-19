package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/**
 * Changes for a list property containing values of type [T]
 * Options are to [deleteValues], [addValuesAtIndex] and/or [addValuesToEnd]
 * This is also the order of operation so mind changed indexes while changing
 */
data class ListValueChanges<T : Any> internal constructor(
    override val reference: IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>, *>,
    val deleteValues: List<T>? = null,
    val addValuesAtIndex: Map<UInt, T>? = null,
    val addValuesToEnd: List<T>? = null
) : DefinedByReference<List<T>> {
    companion object : QueryModel<ListValueChanges<*>, Companion>() {
        val reference by addReference(ListValueChanges<*>::reference)

        val addValuesToEnd by list(
            index = 2u,
            getter = ListValueChanges<*>::addValuesToEnd,
            required = false,
            valueDefinition = valueDefinition
        )

        val addValuesAtIndex by map(
            index = 3u,
            getter = ListValueChanges<*>::addValuesAtIndex,
            keyDefinition = NumberDefinition(type = UInt32),
            valueDefinition = valueDefinition,
            required = false
        )

        val deleteValues by list(
            index = 4u,
            getter = ListValueChanges<*>::deleteValues,
            required = false,
            valueDefinition = valueDefinition
        )

        override fun invoke(values: ObjectValues<ListValueChanges<*>, Companion>) = ListValueChanges<Any>(
            reference = values(reference.index),
            addValuesToEnd = values(addValuesToEnd.index),
            addValuesAtIndex = values(addValuesAtIndex.index),
            deleteValues = values(deleteValues.index)
        )
    }
}

@Suppress("UNCHECKED_CAST")
private val valueDefinition = ContextualValueDefinition(
    contextualResolver = { context: RequestContext? ->
        (context?.reference as ListReference<Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
            ?: throw ContextNotFoundException()
    }
)

/**
 * Convenience infix method to define an array value change
 * Options are to [deleteValues], [addValuesAtIndex] and/or [addValuesToEnd]
 * This is also the order of operation so mind changed indexes while changing
 */
fun <T : Any> IsPropertyReference<List<T>, IsCollectionDefinition<T, List<T>, *, *>, *>.change(
    deleteValues: List<T>? = null,
    addValuesAtIndex: Map<UInt, T>? = null,
    addValuesToEnd: List<T>? = null
) =
    ListValueChanges(
        reference = this,
        addValuesToEnd = addValuesToEnd,
        addValuesAtIndex = addValuesAtIndex,
        deleteValues = deleteValues
    )
