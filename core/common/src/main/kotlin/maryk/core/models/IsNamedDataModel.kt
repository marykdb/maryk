package maryk.core.models

import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

interface IsNamedDataModel<P: IsPropertyDefinitions>: IsDataModel<P> {
    val name: String

    companion object {
        internal fun <DM: IsNamedDataModel<*>> addName(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> String) {
            definitions.add(0, "name", StringDefinition(), getter)
        }
    }
}
