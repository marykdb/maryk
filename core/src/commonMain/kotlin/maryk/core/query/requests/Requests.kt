package maryk.core.query.requests

import maryk.core.inject.InjectWithReference
import maryk.core.models.QuerySingleValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.ObjectValues

/** Object to contain multiple [requests] */
data class Requests internal constructor(
    val requests: List<IsRequest<*>>,
    internal var injectables: List<InjectWithReference>?
) {
    constructor(vararg request: IsRequest<*>): this(request.toList())

    constructor(requests: List<IsRequest<*>>): this(requests, null)

    object Properties: ObjectPropertyDefinitions<Requests>() {
        val requests = add(1, "requests",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = RequestType,
                    definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions,
                    keepAsValues = true
                )
            ),
            Requests::requests,
            fromSerializable = { it.value as IsRequest<*> },
            toSerializable = { TypedValue(it.requestType, it) }
        )

        internal val injectables = add(2, "injectables",
            ContextInjectCollectionOnWriteDefinition(
                definition = ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { InjectWithReference }
                    )
                ),
                valueInjector = { context: RequestContext? ->
                    context?.collectedInjects as List<InjectWithReference>? ?: listOf()
                }
            ),
            Requests::injectables
        )
    }

    /**
     * From the context of JSON/YAML this object only contains a single property.
     * This is however not true for Protobuf. There this object contains a list of injectables.
     */
    @Suppress("UNCHECKED_CAST")
    companion object: QuerySingleValueDataModel<TypedValue<RequestType, Any>, Requests, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.requests as IsPropertyDefinitionWrapper<TypedValue<RequestType, Any>, TypedValue<RequestType, Any>, RequestContext, Requests>
    ) {
        override fun invoke(map: ObjectValues<Requests, Properties>) = Requests(
            requests = map(1),
            injectables = map(2)
        )

        override fun protoBufLengthToAddForField(
            value: Any?,
            definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            cacher: WriteCacheWriter,
            context: RequestContext?
        ): Int {
            val valueToPass = injectValues(definition, context, value)
            return super.protoBufLengthToAddForField(valueToPass, definition, cacher, context)
        }

        override fun writeProtoBufField(
            value: Any?,
            definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            cacheGetter: WriteCacheReader,
            writer: (byte: Byte) -> Unit,
            context: RequestContext?
        ) {
            val valueToPass = injectValues(definition, context, value)
            super.writeProtoBufField(valueToPass, definition, cacheGetter, writer, context)
        }

        /** Inject injectables if it is found on context */
        private fun injectValues(
            definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            context: RequestContext?,
            value: Any?
        ) = if (definition == Properties.injectables && context != null) {
            context.collectedInjects
        } else value

        override fun map(
            context: RequestContext?,
            createMap: Properties.() -> IsValueItems
        ): ObjectValues<Requests, Properties> {
            val map = ObjectValues(this, createMap(this.properties), context)

            val injectables = map.removeFromMap(Properties.injectables.index) as? List<InjectWithReference>?

            injectables?.forEach {
                it.injectInValues(map)
            }

            return map
        }
    }
}
