package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** An operation on a property of type [T] */
interface IsPropertyOperation<T: Any> : IsChange {
    val reference: IsPropertyReference<T, IsPropertyDefinition<T>>
    val valueToCompare: T?

    companion object {
        internal fun <DO: Any> addReference(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) {
            definitions.add(
                0, "reference", ContextCaptureDefinition(
                    ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel?.properties ?: throw ContextNotFoundException()
                        }
                    )
                ) { context, value ->
                    context?.apply {
                        @Suppress("UNCHECKED_CAST")
                        reference = value as IsPropertyReference<*, PropertyDefinitionWrapper<* ,*, *, *, *>>
                    } ?: throw ContextNotFoundException()
                },
                getter
            )
        }

        internal fun <DO: Any> addValueToCompare(definitions: PropertyDefinitions<DO>, getter: (DO) -> Any?) {
            definitions.add(
                1, "valueToCompare",
                ContextualValueDefinition(
                    required = false,
                    contextualResolver = { context: DataModelPropertyContext? ->
                        context?.reference?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                        }?: throw ContextNotFoundException()
                    }
                ),
                getter
            )
        }
    }
}
