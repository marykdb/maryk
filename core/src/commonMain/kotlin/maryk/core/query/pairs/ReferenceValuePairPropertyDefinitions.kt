package maryk.core.query.pairs

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext

/** Defines PropertyDefinitions for a reference value pair, so they can be easily converted inside ReferencePairDataModels */
abstract class ReferenceValuePairPropertyDefinitions<R : DefinedByReference<*>, T: Any, TO: Any, out W: IsDefinitionWrapper<T, TO, RequestContext, R>> :
    ObjectPropertyDefinitions<R>() {
    abstract val reference: ContextualDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, R>
    abstract val value: W
}
