package maryk.core.properties.definitions.contextual

import maryk.core.properties.definitions.IsPropertyDefinition

interface IsDefinitionContextWrapper {
    val definition: IsPropertyDefinition<*>
}
