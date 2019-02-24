package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.FlexBytesPropertyDefinitionWrapper

interface IsNamedDataModel<P : IsPropertyDefinitions> : IsDataModel<P> {
    val name: String

    companion object {
        internal fun <DM : IsNamedDataModel<*>> addName(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> String
        ): FlexBytesPropertyDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM> {
            return definitions.add(1, "name", StringDefinition(), getter)
        }
    }
}
