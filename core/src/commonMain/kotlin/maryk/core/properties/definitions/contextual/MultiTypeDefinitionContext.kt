package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.query.ContainsDefinitionsContext

/** Context to interpret multi type definitions */
class MultiTypeDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var typeEnumName: String? = null

    var definitionMap: Map<MultiTypeEnum<Any>, IsUsableInMultiType<out Any, ContainsDefinitionsContext>>? = null

    private var _multiTypeDefinition: Lazy<MultiTypeDefinition<MultiTypeEnum<Any>, Any, ContainsDefinitionsContext>> = lazy {
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

    val multiTypeDefinition: MultiTypeDefinition<MultiTypeEnum<Any>, Any, ContainsDefinitionsContext> get() = this._multiTypeDefinition.value
}
