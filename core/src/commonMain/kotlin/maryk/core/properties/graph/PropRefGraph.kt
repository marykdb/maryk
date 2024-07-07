package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.graph.PropRefGraphType.Graph
import maryk.core.properties.graph.PropRefGraphType.MapKey
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/**
 * To make graph on Properties to return list of graphables
 * The graphables are sorted after generation so the PropRefGraph can be processed quicker.
 */
@Suppress("UnusedReceiverParameter")
fun <DM : IsValuesDataModel, DMS : IsValuesDataModel> DM.graph(
    embed: EmbeddedValuesDefinitionWrapper<DMS, IsPropertyContext>,
    runner: DMS.() -> List<IsPropRefGraphNode<DMS>>
) = PropRefGraph<DM, DMS>(embed, runner(embed.definition.dataModel).sortedBy { it.index })

/**
 * Represents a Property Reference Graph branch below a [parent] with all [properties] to fetch
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class PropRefGraph<DM : IsValuesDataModel, DMS : IsValuesDataModel> internal constructor(
    val parent: EmbeddedValuesDefinitionWrapper<DMS, IsPropertyContext>,
    override val properties: List<IsPropRefGraphNode<DMS>>
) : IsPropRefGraphNode<DM>, IsTransportablePropRefGraphNode, IsPropRefGraph<DMS> {
    override val index = parent.index
    override val graphType = Graph

    override fun toString() = "Graph { ${renderPropsAsString()} }"

    companion object : ContextualDataModel<PropRefGraph<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        val parent by contextual(
            index = 1u,
            definition = ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    context?.dataModel as? BaseDataModel<*>?
                        ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                context.subDataModel =
                    (value.propertyDefinition as EmbeddedValuesDefinitionWrapper<*, *>).dataModel
            },
            toSerializable = { value: IsDefinitionWrapper<*, *, *, *>?, _ ->
                value?.ref()
            },
            fromSerializable = { value ->
                value?.propertyDefinition as IsDefinitionWrapper<*, *, *, *>?
            },
            getter = PropRefGraph<*, *>::parent
        )

        val properties: ListDefinitionWrapper<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>, IsPropRefGraphNode<Nothing>, GraphContext, PropRefGraph<*, *>> by list(
            index = 2u,
            valueDefinition = InternalMultiTypeDefinition(
                definitionMap = mapOf(
                    Graph to EmbeddedObjectDefinition(
                        dataModel = { this@Companion }
                    ),
                    MapKey to EmbeddedObjectDefinition(
                        dataModel = { GraphMapItem }
                    ),
                    PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = { context: GraphContext? ->
                            context?.subDataModel as? IsValuesDataModel? ?: throw ContextNotFoundException()
                        }
                    )
                ),
                typeEnum = PropRefGraphType
            ),
            getter = PropRefGraph<*, *>::properties,
            toSerializable = { value: IsPropRefGraphNode<*> ->
                value.let {
                    when (it) {
                        is IsDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.ref() as IsTransportablePropRefGraphNode)
                        is PropRefGraph<*, *> -> TypedValue(it.graphType, it)
                        is GraphMapItem<*, *> -> TypedValue(it.graphType, it)
                        else -> throw ParseException("Unknown PropRefGraphType ${it.graphType}")
                    }
                }
            },
            fromSerializable = { value: TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode> ->
                when (value.type) {
                    PropRef -> (value.value as IsPropertyReferenceForValues<*, *, *, *>).propertyDefinition
                    Graph -> value.value as IsPropRefGraphNode<*>
                    MapKey -> value.value as GraphMapItem<*, *>
                }
            }
        )

        override fun invoke(values: ObjectValues<PropRefGraph<*, *>, Companion>): PropRefGraph<*, *> =
            PropRefGraph<IsValuesDataModel, IsValuesDataModel>(
                parent = values(1u),
                properties = values(2u)
            )

        override val Serializer = object: ObjectDataModelSerializer<PropRefGraph<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(this) {
            override fun writeObjectAsJson(
                obj: PropRefGraph<*, *>,
                writer: IsJsonLikeWriter,
                context: GraphContext?,
                skip: List<IsDefinitionWrapper<*, *, *, PropRefGraph<*, *>>>?
            ) {
                val newContext = transformContext(context)
                writeJsonValues(obj.parent.ref(), obj.properties, writer, newContext)
            }

            private fun writeJsonValues(
                reference: AnyPropertyReference,
                listOfPropRefGraphNodes: List<IsPropRefGraphNode<*>>,
                writer: IsJsonLikeWriter,
                context: GraphContext?
            ) {
                parent.capture(context, reference)

                writer.writeStartObject()
                writer.writeFieldName(reference.completeName)
                writePropertiesToJson(listOfPropRefGraphNodes, writer, context)

                writer.writeEndObject()
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: GraphContext?
            ): ObjectValues<PropRefGraph<*, *>, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                if (reader.currentToken !is StartObject) {
                    throw ParseException("JSON value should be an Object")
                }

                reader.nextToken()

                val parentValue = reader.currentToken.let {
                    if (it !is FieldName) {
                        throw ParseException("JSON value should be a FieldName")
                    }

                    val value = it.value ?: throw ParseException("JSON value should not be null")

                    parent.definition.fromString(value, context)
                }
                parent.capture(context, parentValue)

                if (reader.nextToken() !is StartArray) {
                    throw ParseException("JSON value should be an Array")
                }

                var currentToken = reader.nextToken()

                val propertiesValue = mutableListOf<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>>()

                while (currentToken != EndArray && currentToken !is Stopped) {
                    when (currentToken) {
                        is StartObject -> {
                            val newContext = transformContext(context)

                            propertiesValue.add(
                                TypedValue(
                                    Graph,
                                    readJson(reader, newContext).toDataObject()
                                )
                            )
                        }
                        is Value<*> -> {
                            val multiTypeDefinition =
                                properties.valueDefinition as IsMultiTypeDefinition<PropRefGraphType, IsTransportablePropRefGraphNode, GraphContext>

                            propertiesValue.add(
                                TypedValue(
                                    PropRef,
                                    multiTypeDefinition.definition(PropRef)!!.readJson(reader, context)
                                )
                            )
                        }
                        else -> throw ParseException("JSON value should be a String or an Object")
                    }

                    currentToken = reader.nextToken()
                }

                reader.nextToken()

                return values {
                    mapNonNulls(
                        parent withSerializable parentValue,
                        properties withSerializable propertiesValue
                    )
                }
            }
        }
    }
}

/** Write properties to JSON with [writer] in [context] */
internal fun writePropertiesToJson(
    listOfPropRefGraphNodes: List<IsPropRefGraphNode<*>>,
    writer: IsJsonLikeWriter,
    context: GraphContext?
) {
    val transformed = PropRefGraph.properties.toSerializable!!.invoke(Unit, listOfPropRefGraphNodes, context)!!

    writer.writeStartArray()
    for (graphable in transformed) {
        when (val value = graphable.value) {
            is PropRefGraph<*, *> -> PropRefGraph.Serializer.writeObjectAsJson(
                value, writer, context
            )
            is GraphMapItem<*, *> -> GraphMapItem.Serializer.writeObjectAsJson(
                value, writer, context
            )
            is IsDefinitionWrapper<*, *, *, *> -> {
                writer.writeString(value.ref().completeName)
            }
            is AnyPropertyReference -> {
                writer.writeString(value.completeName)
            }
            else -> throw ParseException("Cannot write unknown graphType ${graphable.type}")
        }
    }
    writer.writeEndArray()
}
