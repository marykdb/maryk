package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.values
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType.Graph
import maryk.core.properties.graph.PropRefGraphType.MapKey
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Create a Root graph with references to [properties]
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class RootPropRefGraph<DM : IsRootDataModel> internal constructor(
    override val properties: List<IsPropRefGraphNode<DM>>
) : IsPropRefGraph<DM> {
    companion object : ContextualDataModel<RootPropRefGraph<*>, Companion, ContainsDataModelContext<*>, GraphContext>(
        contextTransformer = {
            GraphContext(it?.dataModel)
        },
    ) {
        val properties: ListDefinitionWrapper<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>, IsPropRefGraphNode<Nothing>, GraphContext, RootPropRefGraph<*>> by list(
            index = 1u,
            valueDefinition = InternalMultiTypeDefinition(
                definitionMap = mapOf(
                    Graph to EmbeddedObjectDefinition(
                        dataModel = { PropRefGraph }
                    ),
                    PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = { context: GraphContext? ->
                            context?.dataModel as? IsValuesDataModel? ?: throw ContextNotFoundException()
                        }
                    ),
                    MapKey to EmbeddedObjectDefinition(
                        dataModel = { GraphMapItem }
                    )
                ),
                typeEnum = PropRefGraphType
            ),
            getter = RootPropRefGraph<*>::properties,
            toSerializable = { value: IsPropRefGraphNode<*> ->
                when (value) {
                    is IsDefinitionWrapper<*, *, *, *> -> TypedValue(value.graphType, value.ref() as IsTransportablePropRefGraphNode)
                    is PropRefGraph<*, *> -> TypedValue(value.graphType, value)
                    is GraphMapItem<*, *> -> TypedValue(value.graphType, value)
                    else -> throw ParseException("Unknown PropRefGraphType ${value.graphType}")
                }
            },
            fromSerializable = { value: TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode> ->
                when (value.type) {
                    PropRef -> (value.value as IsPropertyReferenceForValues<*, *, *, *>).propertyDefinition
                    Graph -> value.value as IsPropRefGraphNode<*>
                    MapKey -> value.value as IsPropRefGraphNode<*>
                }
            }
        )

        override fun invoke(values: ObjectValues<RootPropRefGraph<*>, Companion>): RootPropRefGraph<*> =
            RootPropRefGraph<IsRootDataModel>(
                properties = values(1u)
            )

        override val Serializer = object: ObjectDataModelSerializer<RootPropRefGraph<*>, Companion, ContainsDataModelContext<*>, GraphContext>(this) {
            override fun writeObjectAsJson(
                obj: RootPropRefGraph<*>,
                writer: IsJsonLikeWriter,
                context: GraphContext?,
                skip: List<IsDefinitionWrapper<*, *, *, RootPropRefGraph<*>>>?
            ) {
                writeJsonValues(obj.properties, writer, context)
            }

            private fun writeJsonValues(
                listOfPropRefGraphNodes: List<IsPropRefGraphNode<*>>,
                writer: IsJsonLikeWriter,
                context: GraphContext?
            ) {
                writePropertiesToJson(listOfPropRefGraphNodes, writer, context)
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: GraphContext?
            ): ObjectValues<RootPropRefGraph<*>, Companion> {
                if (reader.currentToken == JsonToken.StartDocument) {
                    reader.nextToken()
                }

                if (reader.currentToken !is JsonToken.StartArray) {
                    throw ParseException("JSON value should be an Array")
                }

                var currentToken = reader.nextToken()

                val propertiesList = mutableListOf<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>>()

                while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                    when (currentToken) {
                        is JsonToken.StartObject -> {
                            val newContext = transformContext(context)

                            propertiesList.add(
                                TypedValue(
                                    Graph,
                                    PropRefGraph.Serializer.readJson(reader, newContext).toDataObject()
                                )
                            )
                        }
                        is JsonToken.Value<*> -> {
                            val multiTypeDefinition =
                                this@Companion.properties.valueDefinition as IsMultiTypeDefinition<PropRefGraphType, IsTransportablePropRefGraphNode, GraphContext>

                            val currentTokenValue = currentToken.value

                            val type = when {
                                currentTokenValue is String && currentTokenValue.contains(char = '[') -> MapKey
                                else -> PropRef
                            }

                            propertiesList.add(
                                TypedValue(
                                    type,
                                    multiTypeDefinition.definition(type)!!.readJson(reader, context)
                                )
                            )
                        }
                        else -> throw ParseException("JSON value should be a String or an Object")
                    }

                    currentToken = reader.nextToken()
                }

                return values {
                    mapNonNulls(
                        this@Companion.properties withSerializable propertiesList
                    )
                }
            }
        }
    }

    override fun toString() = "RootPropRefGraph { ${renderPropsAsString()} }"
}
