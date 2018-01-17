package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Check of property of type [T] */
interface IsPropertyCheck<T: Any> : IsFilter {
    val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>

    companion object {
        fun <DO: Any> addReference(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) {
            definitions.add(
                    index = 0, name = "reference",
                    definition = ContextCaptureDefinition(
                            ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                                    contextualResolver = { it!!.dataModel!!.properties }
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