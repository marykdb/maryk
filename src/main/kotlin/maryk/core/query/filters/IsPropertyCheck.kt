package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.DataObjectProperty
import maryk.core.properties.definitions.wrapper.IsDataObjectValueProperty
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Property Check
 * @param T: type of value to be operated on
 */
interface IsPropertyCheck<T: Any> : IsFilter {
    val reference: IsPropertyReference<T, IsDataObjectValueProperty<T, IsPropertyContext, *>>

    object Properties {
        val reference = ContextCaptureDefinition(
                ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!! }
                )
        ) { context, value ->
            @Suppress("UNCHECKED_CAST")
            context!!.reference = value as IsPropertyReference<*, DataObjectProperty<*, *, *, *>>
        }
    }

    companion object {
        fun <DM: Any> addReference(definitions: PropertyDefinitions<DM>, getter: (DM) -> IsPropertyReference<*, *>?) {
            definitions.add(
                    0, "reference",
                    Properties.reference,
                    getter
            )
        }
    }
}