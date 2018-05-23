package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Value check for a property of type [T] against [value] */
data class Check<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>,
    val value: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.Check

    internal companion object: QueryDataModel<Check<*>>(
        properties = object : PropertyDefinitions<Check<*>>() {
            init {
                DefinedByReference.addReference(this, Check<*>::reference)

                add(1, "value", ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                                ?: throw ContextNotFoundException()
                    }
                ), Check<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Check(
            reference = map(0),
            value = map(1)
        )
    }
}
