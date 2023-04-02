package maryk.core.models

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

abstract class ReferenceValuePairDataModel<R : DefinedByReference<*>, DM: ReferenceValuePairDataModel<R, DM, T, TO, W>, T: Any, TO: Any, W: IsDefinitionWrapper<T, TO, RequestContext, R>>
    : ObjectDataModel<R, DM, RequestContext, RequestContext>(), IsReferenceValuePairDataModel<R, T, TO, W> {
    abstract override fun invoke(values: ObjectValues<R, DM>): R
}
