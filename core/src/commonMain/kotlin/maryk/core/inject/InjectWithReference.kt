package maryk.core.inject

import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference
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

        for(reference in refList) {
            toAddTo = reference.resolveFromAny(toAddTo)
        }

        if (
            this.reference !is ValuePropertyReference<*, *, *, *>
            || toAddTo !is AbstractValues<*, *, *>
        ) {
            throw Exception("Inject can only be contained in Values")
        }

        toAddTo.add(
            index = this.reference.propertyDefinition.index,
            value = this.inject
        )
    }

    internal object Properties : ObjectPropertyDefinitions<InjectWithReference>() {
        val inject = add(1, "inject",
            EmbeddedObjectDefinition(
                dataModel = { Inject }
            ),
            getter = InjectWithReference::inject
        )

        val reference = add(2, "reference",
            ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = { Requests.Properties }
            ),
            getter = InjectWithReference::reference
        )
    }

    internal companion object: QueryDataModel<InjectWithReference, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<InjectWithReference, Properties>) = InjectWithReference(
            inject = values(1),
            reference = values(2)
        )
    }
}
