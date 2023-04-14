package maryk.core.models

import maryk.core.properties.IsPropertyContext

/**
 * Class for DataModels which work with a context to retrieve their properties and values.
 * This is useful for situations where the DataModel definition should depend on outside definitions, or has
 * a need for injection of information from other sources.
 */
abstract class ContextualDataModel<DO: Any, DM: ContextualDataModel<DO, DM, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    val contextTransformer: Unit.(CXI?) -> CX?,
) : TypedObjectDataModel<DO, DM, CXI, CX>(),
    IsObjectDataModel<DO>,
    IsTypedObjectDataModel<DO, DM, CXI, CX>
