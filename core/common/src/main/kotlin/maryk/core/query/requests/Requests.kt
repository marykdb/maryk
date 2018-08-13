package maryk.core.query.requests

import maryk.core.models.QuerySingleValueDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext

/** Object to contain multiple [requests] */
data class Requests(
    val requests: List<IsRequest<*>>
) {
    constructor(vararg request: IsRequest<*>): this(request.toList())

    object Properties: ObjectPropertyDefinitions<Requests>() {
        val requests = add(1, "requests",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = RequestType,
                    definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions
                )
            ),
            Requests::requests,
            fromSerializable = { it.value as IsRequest<*> },
            toSerializable = { TypedValue(it.requestType, it) }
        )
    }

    @Suppress("UNCHECKED_CAST")
    companion object: QuerySingleValueDataModel<TypedValue<RequestType, Any>, Requests, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.requests as IsPropertyDefinitionWrapper<TypedValue<RequestType, Any>, TypedValue<RequestType, Any>, RequestContext, Requests>
    ) {
        override fun invoke(map: ObjectValues<Requests, Properties>) = Requests(
            requests = map(1)
        )
    }
}
