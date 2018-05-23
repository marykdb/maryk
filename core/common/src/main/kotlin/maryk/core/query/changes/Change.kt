package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Change value to [value] for property of type [T] referred by [reference] */
data class Change<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val value: T
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.Change

    internal companion object: QueryDataModel<Change<*>>(
        properties = object : PropertyDefinitions<Change<*>>() {
            init {
                DefinedByReference.addReference(this, Change<*>::reference)

                add(1, "value", ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                            ?: throw ContextNotFoundException()
                    }
                ), Change<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Change(
            reference = map(0),
            value = map(1)
        )
    }
}
