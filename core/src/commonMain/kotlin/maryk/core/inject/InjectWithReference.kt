package maryk.core.inject

import maryk.core.exceptions.RequestException
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.PropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.query.requests.Requests
import maryk.core.values.AbstractValues
import maryk.core.values.ObjectValues

/**
 * With this object you refer to both the Inject and its place of usage with a property reference
 * within a DataObject.
 * This external definition is needed for ProtoBufs where it is not possible to encode the
 * Inject in the place of usage.
 */
internal class InjectWithReference(
    val inject: Inject<*, *>,
    val reference: IsPropertyReference<*, *, *>
) {
    /** Place the inject by reference inside the [values] */
    fun injectInValues(values: AbstractValues<*, *, *>) {
        val refList = this.reference.unwrap().dropLast(1)
        var toAddTo: Any = values

        for (reference in refList) {
            toAddTo = reference.resolveFromAny(toAddTo)
        }

        if (
            this.reference !is PropertyReferenceForValues<*, *, *, *>
            || toAddTo !is AbstractValues<*, *, *>
        ) {
            throw RequestException("Inject can only be contained in Values")
        }

        toAddTo.add(
            index = this.reference.propertyDefinition.index,
            value = this.inject
        )
    }

    internal companion object : QueryModel<InjectWithReference, Companion>() {
        val inject by embedObject(
            index = 1u,
            getter = InjectWithReference::inject,
            dataModel = { Inject.Model }
        )

        val reference by contextual(
            index = 2u,
            getter = InjectWithReference::reference,
            definition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = { Requests.Companion }
            )
        )

        override fun invoke(values: ObjectValues<InjectWithReference, Companion>) = InjectWithReference(
            inject = values(1u),
            reference = values(2u)
        )
    }
}
