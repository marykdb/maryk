package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Property Check
 * @param T: type of value to be operated on
 */
interface IsPropertyCheck<T: Any> : IsFilter {
    val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>

    object Properties {
        val reference = ContextCaptureDefinition(
                ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        name = "reference",
                        index = 0,
                        contextualResolver = { it!!.dataModel!! }
                )
        ) { context, value ->
            @Suppress("UNCHECKED_CAST")
            context!!.reference = value as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
        }
    }
}