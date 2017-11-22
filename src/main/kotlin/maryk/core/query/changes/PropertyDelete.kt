package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference

/** Delete of a property
 * @param reference to property which is to be/was deleted
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
data class PropertyDelete<T: Any>(
        override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>,
        override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.PROP_DELETE

    companion object: QueryDataModel<PropertyDelete<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                PropertyDelete(
                        reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                        valueToCompare = it[1]
                )
            },
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, PropertyDelete<*>::reference),
                    Def(IsPropertyOperation.Properties.valueToCompare, PropertyDelete<*>::valueToCompare)
            )
    )
}