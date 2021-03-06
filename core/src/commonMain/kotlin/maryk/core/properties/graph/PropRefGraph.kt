package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.graph.PropRefGraphType.Graph
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
 * To make graph with [runner] on Properties to return list of graphables
 * The graphables are sorted after generation so the PropRefGraph can be processed quicker.
 */
@Suppress("unused")
fun <P : PropertyDefinitions, DM : IsValuesDataModel<PS>, PS : PropertyDefinitions> P.graph(
    embed: EmbeddedValuesDefinitionWrapper<DM, PS, IsPropertyContext>,
    runner: PS.() -> List<IsPropRefGraphNode<PS>>
) = PropRefGraph<P, DM, PS>(embed, runner(embed.definition.dataModel.properties).sortedBy { it.index })

/**
 * Represents a Property Reference Graph branch below a [parent] with all [properties] to fetch
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class PropRefGraph<P : PropertyDefinitions, DM : IsValuesDataModel<PS>, PS : PropertyDefinitions> internal constructor(
    val parent: EmbeddedValuesDefinitionWrapper<DM, PS, IsPropertyContext>,
    override val properties: List<IsPropRefGraphNode<PS>>
) : IsPropRefGraphNode<P>, IsTransportablePropRefGraphNode, IsPropRefGraph<PS> {
    override val index = parent.index
    override val graphType = Graph

    override fun toString() = "Graph { ${renderPropsAsString()} }"

    object Properties : ObjectPropertyDefinitions<PropRefGraph<*, *, *>>() {
        val parent by contextual(
            index = 1u,
            definition = ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    context?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                        ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                context.subDataModel =
                    (value.propertyDefinition as EmbeddedValuesDefinitionWrapper<*, *, *>).dataModel
            },
            toSerializable = { value: IsDefinitionWrapper<*, *, *, *>?, _ ->
                value?.ref()
            },
            fromSerializable = { value ->
                value?.propertyDefinition as IsDefinitionWrapper<*, *, *, *>?
            },
            getter = PropRefGraph<*, *, *>::parent
        )

        val properties by list(
            index = 2u,
            valueDefinition = InternalMultiTypeDefinition(
                definitionMap = mapOf(
                    Graph to EmbeddedObjectDefinition(
                        dataModel = { PropRefGraph }
                    ),
                    PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = { context: GraphContext? ->
                            context?.subDataModel?.properties as? PropertyDefinitions? ?: throw ContextNotFoundException()
                        }
                    )
                ),
                typeEnum = PropRefGraphType
            ),
            getter = PropRefGraph<*, *, *>::properties,
            toSerializable = { value: IsPropRefGraphNode<*> ->
                value.let {
                    when (it) {
                        is IsDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.ref() as IsTransportablePropRefGraphNode)
                        is PropRefGraph<*, *, *> -> TypedValue(it.graphType, it)
                        else -> throw ParseException("Unknown PropRefGraphType ${it.graphType}")
                    }
                }
            },
            fromSerializable = { value: TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode> ->
                when (value.type) {
                    PropRef -> (value.value as IsPropertyReferenceForValues<*, *, *, *>).propertyDefinition
                    Graph -> value.value as IsPropRefGraphNode<*>
                }
            }
        )
    }

    companion object :
        ContextualDataModel<PropRefGraph<*, *, *>, Properties, ContainsDataModelContext<*>, GraphContext>(
            properties = Properties,
            contextTransformer = {
                if (it is GraphContext && it.subDataModel != null) {
                    GraphContext(it.subDataModel)
                } else {
                    GraphContext(it?.dataModel)
                }
            }
        ) {
        override fun invoke(values: ObjectValues<PropRefGraph<*, *, *>, Properties>) =
            PropRefGraph<PropertyDefinitions, IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                parent = values(1u),
                properties = values(2u)
            )

        override fun writeJson(obj: PropRefGraph<*, *, *>, writer: IsJsonLikeWriter, context: GraphContext?) {
            writeJsonValues(obj.parent.ref(), obj.properties, writer, context)
        }

        private fun writeJsonValues(
            reference: AnyPropertyReference,
            listOfPropRefGraphNodes: List<IsPropRefGraphNode<*>>,
            writer: IsJsonLikeWriter,
            context: GraphContext?
        ) {
            Properties.parent.capture(context, reference)

            writer.writeStartObject()
            writer.writeFieldName(reference.completeName)
            writePropertiesToJson(listOfPropRefGraphNodes, writer, context)

            writer.writeEndObject()
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: GraphContext?
        ): ObjectValues<PropRefGraph<*, *, *>, Properties> {
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

                Properties.parent.definition.fromString(value, context)
            }
            Properties.parent.capture(context, parentValue)

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
                                PropRefGraph.readJson(reader, newContext).toDataObject()
                            )
                        )
                    }
                    is Value<*> -> {
                        val multiTypeDefinition =
                            Properties.properties.valueDefinition as IsMultiTypeDefinition<PropRefGraphType, IsTransportablePropRefGraphNode, GraphContext>

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

            return this.values {
                mapNonNulls(
                    parent withSerializable parentValue,
                    properties withSerializable propertiesValue
                )
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
    val transformed = PropRefGraph.Properties.properties.toSerializable!!.invoke(Unit, listOfPropRefGraphNodes, context)!!

    writer.writeStartArray()
    for (graphable in transformed) {
        when (val value = graphable.value) {
            is PropRefGraph<*, *, *> -> PropRefGraph.writeJson(
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
