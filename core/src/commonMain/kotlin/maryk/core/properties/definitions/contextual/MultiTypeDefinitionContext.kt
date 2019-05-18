package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.query.ContainsDefinitionsContext

/** Context to interpret multi type definitions */
class MultiTypeDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var multiTypeEnumDefinition: MultiTypeEnumDefinition<MultiTypeEnum<*>>? = null

    val multiTypeDefinition by lazy {
        MultiTypeDefinition(
            typeEnum = multiTypeEnumDefinition!!
        )
    }
}
