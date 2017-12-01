package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.query.DataModelPropertyContext

/** Changes for a set property
 * @param reference to set property affected by the change
 * @param addValues values to add to set
 * @param deleteValues values to delete from set
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
data class SetPropertyChange<T: Any>(
        override val reference: IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>,
        val addValues: Set<T>? = null,
        val deleteValues: Set<T>? = null,
        override val valueToCompare: Set<T>? = null
) : IsPropertyOperation<Set<T>> {
    override val changeType = ChangeType.SET_CHANGE

    internal object Properties : PropertyDefinitions<SetPropertyChange<*>>() {
        @Suppress("UNCHECKED_CAST")
        private val valueDefinition = ContextualValueDefinition(contextualResolver = { context: DataModelPropertyContext? ->
            (context!!.reference!! as SetReference<Any, IsPropertyContext>).propertyDefinition.valueDefinition
        })
        @Suppress("UNCHECKED_CAST")
        val valueToCompare = ContextualCollectionDefinition(
                name = "valueToCompare",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    (context!!.reference!! as SetReference<Any, IsPropertyContext>).propertyDefinition as IsByteTransportableCollection<Any, Collection<Any>, DataModelPropertyContext>
                }
        )
        val addValues = SetDefinition(
                name = "addValues",
                index = 2,
                valueDefinition = valueDefinition
        )
        val deleteValues = SetDefinition(
                name = "deleteValues",
                index = 3,
                valueDefinition = valueDefinition
        )
    }

    companion object: QueryDataModel<SetPropertyChange<*>>(
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, SetPropertyChange<*>::reference),
                    Def(Properties.valueToCompare, SetPropertyChange<*>::valueToCompare),
                    Def(Properties.addValues, SetPropertyChange<*>::addValues),
                    Def(Properties.deleteValues, SetPropertyChange<*>::deleteValues)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = SetPropertyChange(
                reference = map[0] as IsPropertyReference<Set<Any>, AbstractValueDefinition<Set<Any>, IsPropertyContext>>,
                valueToCompare = map[1] as Set<Any>?,
                addValues = map[2] as Set<Any>?,
                deleteValues = map[3] as Set<Any>?
        )
    }
}