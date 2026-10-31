package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsMapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Create a graph node for a map [key]
 */
operator fun <K : Any> IsMapDefinitionWrapper<K, *, *, *, *>.get(
    key: K
) = GraphMapItem<K, IsDataModel>(
    mapReference = this.ref() as IsMapReference<*, *, *, *>,
    key = key
)

/**
 * Create a graph node for a map [key] and build a sub graph on the embedded value
 */
@Suppress("UNCHECKED_CAST")
fun <K : Any, DMS : IsValuesDataModel> IsMapDefinitionWrapper<K, Values<DMS>, *, *, *>.graph(
    key: K,
    graphGetter: DMS.() -> List<IsPropRefGraphNode<DMS>>
) = GraphMapItem<K, IsDataModel>(
    mapReference = this.ref() as IsMapReference<*, *, *, *>,
    key = key,
    properties = graphGetter((this.definition.valueDefinition as EmbeddedValuesDefinition<DMS>).dataModel).sortedBy { it.index } as List<IsPropRefGraphNode<IsDataModel>>
)

/**
 * Represents selection of a specific map [key] optionally with a sub graph of [properties]
 */
data class GraphMapItem<K : Any, DM : IsDataModel> internal constructor(
    val mapReference: IsMapReference<*, *, *, *>,
    val key: K,
    override val properties: List<IsPropRefGraphNode<IsDataModel>> = emptyList()
) : IsPropRefGraphNode<DM>, IsTransportablePropRefGraphNode, IsPropRefGraph<IsDataModel> {
    override val index = mapReference.index
    override val graphType = PropRefGraphType.MapKey

    override fun toString() = buildString {
        append("${mapReference.name}[$key]")
        if (properties.isNotEmpty()) {
            append(" {")
            append(renderPropsAsString())
            append("}")
        }
    }

    companion object : ContextualDataModel<GraphMapItem<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                it.toGraphContext(it.subDataModel)
            } else {
                it.toGraphContext(it?.dataModel)
            }
        }
    ) {
        val mapReference by contextual(
            index = 1u,
            getter = GraphMapItem<*, *>::mapReference,
            definition = ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    (context?.subDataModel ?: context?.dataModel) as? IsValuesDataModel?
                        ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                requireLocalGraphReference(value)
                @Suppress("UNCHECKED_CAST")
                val mapRef = value as IsMapReference<Comparable<Any>, Any, IsPropertyContext, IsMapDefinitionWrapper<Comparable<Any>, Any, Any, IsPropertyContext, Any>>
                context.reference = mapRef
                val valueDef = mapRef.propertyDefinition.definition.valueDefinition
                if (valueDef is EmbeddedValuesDefinition<*>) {
                    context.subDataModel = valueDef.dataModel
                }
            }
        )

        val key by contextual(
            index = 2u,
            getter = GraphMapItem<*, *>::key,
            definition = ContextualSubDefinition(
                contextualResolver = { context: GraphContext? ->
                    @Suppress("UNCHECKED_CAST")
                    (context?.reference as IsMapReference<Comparable<Any>, Any, IsPropertyContext, IsMapDefinitionWrapper<Comparable<Any>, Any, Any, IsPropertyContext, Any>>?)
                        ?.propertyDefinition?.definition?.keyDefinition
                        ?: throw ContextNotFoundException()
                }
            )
        )

        @Suppress("UNCHECKED_CAST")
        val properties: ListDefinitionWrapper<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>, IsPropRefGraphNode<Nothing>, GraphContext, GraphMapItem<*, *>> by list(
            index = 3u,
            required = false,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                definitionMap = mapOf(
                    PropRefGraphType.Graph to EmbeddedObjectDefinition(dataModel = { PropRefGraph }),
                    PropRefGraphType.MapKey to EmbeddedObjectDefinition(dataModel = { GraphMapItem }),
                    PropRefGraphType.PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = { context: GraphContext? ->
                            context?.subDataModel as? IsValuesDataModel? ?: throw ContextNotFoundException()
                        }
                    ),
                    PropRefGraphType.TypeGraph to EmbeddedObjectDefinition(dataModel = { TypePropRefGraph })
                ),
                typeEnum = PropRefGraphType
            ),
            getter = GraphMapItem<*, *>::properties as (GraphMapItem<*, *>) -> List<IsPropRefGraphNode<Nothing>>,
            toSerializable = { value: IsPropRefGraphNode<*> ->
                when (value) {
                    is IsDefinitionWrapper<*, *, *, *> -> TypedValue(value.graphType, value.ref() as IsTransportablePropRefGraphNode)
                    is PropRefGraph<*, *> -> TypedValue(value.graphType, value)
                    is GraphMapItem<*, *> -> TypedValue(value.graphType, value)
                    is TypePropRefGraph<*, *, *> -> TypedValue(value.graphType, value)
                    else -> throw ParseException("Unknown PropRefGraphType ${value.graphType}")
                }
            },
            fromSerializable = { value: TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode> ->
                when (value.type) {
                    PropRefGraphType.PropRef -> graphPropertyDefinition(value.value)
                    PropRefGraphType.Graph -> value.value as IsPropRefGraphNode<*>
                    PropRefGraphType.MapKey -> value.value as GraphMapItem<*, *>
                    PropRefGraphType.TypeGraph -> value.value as TypePropRefGraph<*, *, *>
                }
            }
        )

        override fun invoke(values: ObjectValues<GraphMapItem<*, *>, Companion>): GraphMapItem<*, *> = GraphMapItem<Any, IsDataModel>(
            mapReference = values(1u),
            key = values(2u),
            properties = values<List<IsPropRefGraphNode<IsDataModel>>>(3u).sortedBy { it.index }
        )

        override val Serializer = object : ObjectDataModelSerializer<GraphMapItem<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(this) {
            override fun writeObjectAsJson(
                obj: GraphMapItem<*, *>,
                writer: IsJsonLikeWriter,
                context: GraphContext?,
                skip: List<IsDefinitionWrapper<*, *, *, GraphMapItem<*, *>>>?
            ) {
                if (obj.properties.isEmpty()) {
                    writer.writeString(obj.jsonMapKeyName(context))
                } else {
                    writer.writeStartObject()
                    writer.writeFieldName(obj.jsonMapKeyName(context))
                    writePropertiesToJson(obj.properties, writer, context)
                    writer.writeEndObject()
                }
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: GraphContext?
            ): ObjectValues<GraphMapItem<*, *>, Companion> {
                if (reader.currentToken == JsonToken.StartDocument) reader.nextToken()
                if (reader.currentToken is JsonToken.StartObject) {
                    val node = readGraphNodeFromJson(reader, context)
                    val graph = node.value as? GraphMapItem<*, *> ?: throw ParseException("Expected map selection")
                    return graphMapValues(graph.mapReference, graph.key,
                        requireNotNull(properties.toSerializable).invoke(graph.properties, context)!!, context)
                }
                val name = (reader.currentToken as? JsonToken.Value<*>)?.value as? String
                    ?: throw ParseException("Expected map selection string or object")
                val key = readGraphMapKey(name, context)
                return graphMapValues(key.reference, key.key, emptyList(), context)
            }
        }
    }
}

