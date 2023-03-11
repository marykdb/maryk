package maryk.core.query.requests

import maryk.core.definitions.Operation.Request
import maryk.core.inject.InjectWithReference
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.ObjectValues

/** Object to contain multiple [requests] */
data class Requests internal constructor(
    val requests: List<IsTransportableRequest<*>>,
    internal var injectables: List<InjectWithReference>?
): IsOperation {
    override val operationType = Request

    constructor(vararg request: IsTransportableRequest<*>) : this(request.toList())

    constructor(requests: List<IsTransportableRequest<*>>) : this(requests, null)

    /**
     * From the context of JSON/YAML this object only contains a single property.
     * This is however not true for Protobuf. There this object contains a list of injectables.
     */
    companion object : QueryModel<Requests, Companion>() {
        val requests by list(
            index = 1u,
            getter = Requests::requests,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions,
                keepAsValues = true
            ),
            fromSerializable = { it.value },
            toSerializable = {
                @Suppress("UNCHECKED_CAST")
                when (it) {
                    is TypedValue<*, *> -> it as TypedValue<RequestType, Any>
                    is IsTransportableRequest<*> -> TypedValue(it.requestType, it)
                    else -> throw Exception("Unknown type for MultiType")
                }
            }
        )

        internal val injectables by contextual(
            index = 2u,
            getter = Requests::injectables,
            definition = ContextInjectCollectionOnWriteDefinition(
                definition = ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { InjectWithReference }
                    )
                ),
                valueInjector = { context: RequestContext? ->
                    context?.collectedInjects as List<InjectWithReference>? ?: listOf()
                }
            )
        )

        override fun invoke(values: ObjectValues<Requests, Companion>): Requests =
            Model.invoke(values)

        @Suppress("UNCHECKED_CAST")
        override val Model = object: SingleTypedValueDataModel<TypedValue<RequestType, Any>, Requests, Companion, RequestContext>(
            properties = this@Companion,
            singlePropertyDefinitionGetter = { requests as IsDefinitionWrapper<TypedValue<RequestType, Any>, TypedValue<RequestType, Any>, RequestContext, Requests> }
        ) {
            override fun invoke(values: ObjectValues<Requests, Companion>) = Requests(
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
            ) = if (definition == injectables && context != null) {
                context.collectedInjects
            } else value

            override fun values(
                context: RequestContext?,
                createValues: Companion.() -> IsValueItems
            ): ObjectValues<Requests, Companion> {
                val map = ObjectValues(this, createValues(this@Companion), context)

                val injectables = map.remove(injectables.index) as? List<InjectWithReference>?

                injectables?.forEach {
                    it.injectInValues(map)
                }

                return map
            }
        }
    }

}
