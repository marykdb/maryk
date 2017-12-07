package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Property Check
 * @param T: type of value to be operated on
 */
interface IsPropertyCheck<T: Any> : IsFilter {
    val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>

    companion object {
        fun <DM: Any> addReference(definitions: PropertyDefinitions<DM>, getter: (DM) -> IsPropertyReference<*, *>?) {
            definitions.add(
                    index = 0, name = "reference",
                    definition = ContextCaptureDefinition(
                            ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                                    contextualResolver = { it!!.dataModel!! }
                            )
                    ) { context, value ->
                        @Suppress("UNCHECKED_CAST")
                        context!!.reference = value as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *>>
                    },
                    getter = getter
            )
        }
    }
}