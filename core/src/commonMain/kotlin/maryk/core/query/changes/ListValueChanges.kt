package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/**
 * Changes for a list property containing values of type [T]
 * Options are to [deleteValues], [addValuesAtIndex] and/or [addValuesToEnd]
 * This is also the order of operation so mind changed indices while changing
 */
data class ListValueChanges<T : Any> internal constructor(
    override val reference: IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>, *>,
    val deleteValues: List<T>? = null,
    val addValuesAtIndex: Map<UInt, T>? = null,
    val addValuesToEnd: List<T>? = null
) : DefinedByReference<List<T>> {
    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ListValueChanges<*>>() {
        val reference = DefinedByReference.addReference(this, ListValueChanges<*>::reference)

        val addValuesToEnd = add(2u, "addValuesToEnd", valueListDefinition, ListValueChanges<*>::addValuesToEnd)

        val addValuesAtIndex = add(
            3u, "addValuesAtIndex", MapDefinition(
                required = false,
                keyDefinition = NumberDefinition(type = UInt32),
                valueDefinition = valueDefinition
            ), ListValueChanges<*>::addValuesAtIndex
        )

        val deleteValues = add(4u, "deleteValues", valueListDefinition, ListValueChanges<*>::deleteValues)
    }

    companion object : QueryDataModel<ListValueChanges<*>, Properties>(
        properties = Properties
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(values: ObjectValues<ListValueChanges<*>, Properties>) = ListValueChanges<Any>(
            reference = values(1u),
            addValuesToEnd = values(2u),
            addValuesAtIndex = values(3u),
            deleteValues = values(4u)
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

private val valueListDefinition = ListDefinition(
    required = false,
    valueDefinition = valueDefinition
)

/**
 * Convenience infix method to define an array value change
 * Options are to [deleteValues], [addValuesAtIndex] and/or [addValuesToEnd]
 * This is also the order of operation so mind changed indices while changing
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
