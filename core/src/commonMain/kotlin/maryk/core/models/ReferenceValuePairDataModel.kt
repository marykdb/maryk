package maryk.core.models

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext

/**
 * DataModel which defines a Reference value pair. These are used within specific Request structures.
 */
abstract class ReferenceValuePairDataModel<R : DefinedByReference<*>, DM: ReferenceValuePairDataModel<R, DM, T, TO, W>, T: Any, TO: Any, W: IsDefinitionWrapper<T, TO, RequestContext, R>>
    : InternalObjectDataModel<R, DM, RequestContext, RequestContext>(), IsReferenceValuePairDataModel<R, T, TO, W>
