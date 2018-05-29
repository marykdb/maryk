package maryk.core.query.requests

import maryk.core.objects.QuerySingleValueDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelPropertyContext

/** Object to contain multiple requests */
data class Requests(
    val requests: List<IsRequest>
) {
    constructor(vararg request: IsRequest): this(request.toList())

    internal object Properties: PropertyDefinitions<Requests>() {
        val requests = add(0, "requests",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = RequestType,
                    definitionMap = mapOfRequestTypeSubModelDefinitions
                )
            ),
            Requests::requests,
            fromSerializable = { it.value as IsRequest },
            toSerializable = { TypedValue(it.requestType, it) }
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal companion object: QuerySingleValueDataModel<TypedValue<RequestType, Any>, Requests>(
        properties = Properties,
        singlePropertyDefinition = Properties.requests as IsPropertyDefinitionWrapper<TypedValue<RequestType, Any>, *, DataModelPropertyContext, Requests>
    ) {
        override fun invoke(map: Map<Int, *>) = Requests(
            requests = map(0)
        )
    }
}