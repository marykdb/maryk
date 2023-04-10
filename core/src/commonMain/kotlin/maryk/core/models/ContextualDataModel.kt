package maryk.core.models

import maryk.core.properties.IsPropertyContext

/**
 * DataModel containing objects of type [DO] which create contexts so properties know the values of other properties.
 * Use it to create a context on starting an action which needs a context.
 * This context is cached if it is needed to read multiple times
 */
abstract class ContextualDataModel<DO: Any, P: ContextualDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    val contextTransformer: Unit.(CXI?) -> CX?,
) : InternalObjectDataModel<DO, P, CXI, CX>(),
    IsObjectDataModel<DO>,
    IsTypedObjectModel<DO, P, CXI, CX>
