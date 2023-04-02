package maryk.core.models

import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext

/** Defines DataModel for a reference value pair, so they can be easily converted inside ReferencePairDataModels */
interface IsReferenceValuePairDataModel<R : DefinedByReference<*>, T: Any, TO: Any, out W: IsDefinitionWrapper<T, TO, RequestContext, R>> :
    IsObjectDataModel<R> {
    val reference: ContextualDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, R>
    val value: W
}
