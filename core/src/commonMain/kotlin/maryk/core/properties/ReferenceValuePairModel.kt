package maryk.core.properties

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues


/** Defines PropertyDefinitions for a reference value pair, so they can be easily converted inside ReferencePairDataModels */
interface IsReferenceValuePairModel<R : DefinedByReference<*>, T: Any, TO: Any, out W: IsDefinitionWrapper<T, TO, RequestContext, R>> : IsObjectPropertyDefinitions<R> {
    val reference: ContextualDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, R>
    val value: W
}

abstract class ReferenceValuePairModel<R : DefinedByReference<*>, P: ReferenceValuePairModel<R, P, T, TO, W>, T: Any, TO: Any, W: IsDefinitionWrapper<T, TO, RequestContext, R>>
    : ObjectPropertyDefinitions<R>(), IsInternalModel<R, P, RequestContext, RequestContext>, IsReferenceValuePairModel<R, T, TO, W> {
    abstract fun invoke(values: ObjectValues<R, P>): R

    @Suppress("UNCHECKED_CAST")
    override val Model = object: QueryDataModel<R, P>(
        this@ReferenceValuePairModel as P,
    ) {
        override fun invoke(values: ObjectValues<R, P>): R =
            this@ReferenceValuePairModel.invoke(values)
    }
}
