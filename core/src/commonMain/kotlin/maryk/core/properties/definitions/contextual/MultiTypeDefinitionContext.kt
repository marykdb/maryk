package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.ContainsDefinitionsContext

/** Context to interpret multi type definitions */
class MultiTypeDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var typeEnumName: String? = null

    var definitionMap: Map<TypeEnum<Any>, IsSubDefinition<out Any, ContainsDefinitionsContext>>? = null

    private var _multiTypeDefinition: Lazy<MultiTypeDefinition<TypeEnum<Any>, Any, ContainsDefinitionsContext>> = lazy {
        val typeOptions = definitionMap?.keys?.toTypedArray() ?: throw ContextNotFoundException()

        val typeEnum = IndexedEnumDefinition(
            typeEnumName ?: throw ContextNotFoundException(),
            { typeOptions }
        )

        MultiTypeDefinition(
            typeEnum = typeEnum,
            definitionMap = this.definitionMap ?: throw ContextNotFoundException()
        )
    }

    val multiTypeDefinition: MultiTypeDefinition<TypeEnum<Any>, Any, ContainsDefinitionsContext> get() = this._multiTypeDefinition.value
}
