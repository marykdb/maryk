package maryk.core.query.requests

import maryk.core.definitions.Operation.Request
import maryk.core.inject.InjectWithReference
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
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
): IsOperation {
    override val operationType = Request

    constructor(vararg request: IsRequest<*>) : this(request.toList())

    constructor(requests: List<IsRequest<*>>) : this(requests, null)

    object Properties : ObjectPropertyDefinitions<Requests>() {
        val requests = add(1u, "requests",
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

        internal val injectables = add(2u, "injectables",
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
    companion object : SingleTypedValueDataModel<TypedValue<RequestType, Any>, Requests, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.requests as IsDefinitionWrapper<TypedValue<RequestType, Any>, TypedValue<RequestType, Any>, RequestContext, Requests>
    ) {
        override fun invoke(values: ObjectValues<Requests, Properties>) = Requests(
            requests = values(1u),
            injectables = values(2u)
        )

        override fun protoBufLengthToAddForField(
            value: Any?,
            definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            cacher: WriteCacheWriter,
            context: RequestContext?
        ): Int {
            val valueToPass = injectValues(definition, context, value)
            return super.protoBufLengthToAddForField(valueToPass, definition, cacher, context)
        }

        override fun writeProtoBufField(
            value: Any?,
            definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            cacheGetter: WriteCacheReader,
            writer: (byte: Byte) -> Unit,
            context: RequestContext?
        ) {
            val valueToPass = injectValues(definition, context, value)
            super.writeProtoBufField(valueToPass, definition, cacheGetter, writer, context)
        }

        /** Inject injectables if it is found on context */
        private fun injectValues(
            definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, Requests>,
            context: RequestContext?,
            value: Any?
        ) = if (definition == Properties.injectables && context != null) {
            context.collectedInjects
        } else value

        override fun values(
            context: RequestContext?,
            createValues: Properties.() -> IsValueItems
        ): ObjectValues<Requests, Properties> {
            val map = ObjectValues(this, createValues(this.properties), context)

            val injectables = map.remove(Properties.injectables.index) as? List<InjectWithReference>?

            injectables?.forEach {
                it.injectInValues(map)
            }

            return map
        }
    }
}
