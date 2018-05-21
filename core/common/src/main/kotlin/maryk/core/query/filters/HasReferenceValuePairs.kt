package maryk.core.query.filters

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.pairs.ReferenceValuePair

interface HasReferenceValuePairs {
    val referenceValuePairs: List<ReferenceValuePair<*>>

    companion object {
        internal fun <T: Any, DO: Any> addReferenceValuePairs(definitions: PropertyDefinitions<DO>, getter: (DO) -> List<ReferenceValuePair<T>>?) =
            definitions.add(0, "referenceValuePairs",
                ListDefinition(
                    valueDefinition = SubModelDefinition(
                        dataModel = {
                            @Suppress("UNCHECKED_CAST")
                            ReferenceValuePair as SimpleDataModel<ReferenceValuePair<T>, PropertyDefinitions<ReferenceValuePair<T>>>
                        }
                    )
                ),
                getter = getter
            )
    }
}
