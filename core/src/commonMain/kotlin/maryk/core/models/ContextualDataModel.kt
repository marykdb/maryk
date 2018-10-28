package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions

/**
 * ObjectDataModel of type [DO] which create contexts so [properties] know the values of other properties.
 * Use it to create a context on starting an action which needs a context.
 * This context is cached if it is needed to read multiple times
 */
abstract class ContextualDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext>(
    properties: P,
    private val contextTransformer: (CXI?) -> CX?
) : AbstractObjectDataModel<DO, P, CXI, CX>(properties) {
    override fun transformContext(context: CXI?) = this.contextTransformer(context)
}
