package maryk.core.objects.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Get a graph of an embedded object */
fun <PDO: Any, DO: Any> IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>.graph(
    vararg property: IsGraphable<DO>
) = Graph(this, property.toList())

/**
 * Represents a Graph branch below a [parent] with all [properties] to fetch
 */
data class Graph<PDO: Any, DO: Any> internal constructor(
    val parent: IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>,
    val properties: List<IsGraphable<DO>>
) : IsGraphable<PDO> {
    override val graphType = GraphType.Graph

    internal object Properties : PropertyDefinitions<Graph<*, *>>() {
        val parent = add(0, "parent",
            ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    context?.dataModel?.properties ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                context.subDataModel = (value.propertyDefinition as EmbeddedObjectPropertyDefinitionWrapper<*, *, *, *, *, *, *>).dataModel
            },
            toSerializable = { value: IsPropertyDefinitionWrapper<*, *, *, *>?, _ ->
                value?.getRef()
            },
            fromSerializable = { value ->
                value?.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>?
            },
            getter = Graph<*, *>::parent
        )

        val properties = addProperties(1, Graph<*, *>::properties) { context: GraphContext? ->
            context?.subDataModel?.properties ?: throw ContextNotFoundException()
        }
    }

    internal companion object : ContextualDataModel<Graph<*, *>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Graph<Any, Any>(
            parent = map(0),
            properties = map(1)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: GraphContext?) {
            val reference = map[Properties.parent.index] as IsPropertyReference<*, *>
            @Suppress("UNCHECKED_CAST")
            val listOfGraphables = map[Properties.properties.index] as List<IsGraphable<*>>

            this.writeJsonValues(reference, listOfGraphables, writer, context)
        }

        override fun writeJson(obj: Graph<*, *>, writer: IsJsonLikeWriter, context: GraphContext?) {
            this.writeJsonValues(obj.parent.getRef(), obj.properties, writer, context)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun writeJsonValues(
            reference: IsPropertyReference<*, *>,
            listOfGraphables: List<IsGraphable<*>>,
            writer: IsJsonLikeWriter,
            context: GraphContext?
        ) {
            Properties.parent.capture(context, reference)

            writer.writeStartObject()
            writer.writeFieldName(reference.completeName)
            writePropertiesToJson(listOfGraphables, writer, context)

            writer.writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: GraphContext?): Map<Int, Any> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("JSON value should be an Object")
            }

            reader.nextToken()

            val parent = reader.currentToken.let {
                val value = if (it !is JsonToken.FieldName || it.value == null) {
                    throw ParseException("JSON value should be a non empty FieldName")
                } else {
                    it.value!!
                }

                Properties.parent.definition.fromString(value, context)
            }
            Properties.parent.capture(context, parent)

            if (reader.nextToken() !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val properties = mutableListOf<TypedValue<GraphType,*>>()

            while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                when (currentToken) {
                    is JsonToken.StartObject -> {
                        val newContext = Graph.transformContext(context)

                        properties.add(
                            TypedValue(
                                GraphType.Graph,
                                Graph.readJsonToObject(reader, newContext)
                            )
                        )
                    }
                    is JsonToken.Value<*> -> {
                        val multiTypeDefinition = Properties.properties.valueDefinition as MultiTypeDefinition<GraphType, GraphContext>

                        properties.add(
                            TypedValue(
                                GraphType.PropRef,
                                multiTypeDefinition.definitionMap[GraphType.PropRef]!!
                                    .readJson(reader, context) as IsPropertyReference<*, *>
                            )
                        )
                    }
                    else -> throw ParseException("JSON value should be a String or an Object")
                }

                currentToken = reader.nextToken()
            }

            reader.nextToken()


            return mapOf(
                Properties.parent.index to parent,
                Properties.properties.index to properties
            )
        }
    }
}

/**
 * Add properties to Graph objects so they are encodable
 */
internal fun <DO: Any> PropertyDefinitions<DO>.addProperties(
    index: Int,
    getter: (DO) -> List<IsGraphable<*>>,
    contextResolver: (GraphContext?) -> PropertyDefinitions<*>
) =
    add(index, "properties",
        ListDefinition(
            valueDefinition = MultiTypeDefinition(
                definitionMap = mapOf(
                    GraphType.Graph to EmbeddedObjectDefinition(
                        dataModel = { Graph }
                    ),
                    GraphType.PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = contextResolver
                    )
                ),
                typeEnum = GraphType
            )
        ),
        toSerializable = { value: IsGraphable<*> ->
            value.let {
                when (it) {
                    is IsPropertyDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.getRef())
                    is Graph<*, *> -> TypedValue(it.graphType, it)
                    else -> throw ParseException("Unknown GraphType ${it.graphType}")
                }
            }
        },
        fromSerializable = { value: TypedValue<GraphType, *> ->
            when (value.value) {
                is IsPropertyReference<*, *> -> value.value.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>
                is Graph<*, *> -> value.value
                else -> throw ParseException("Unknown GraphType ${value.type}")
            }
        },
        getter = getter
    )

/** Write properties to JSON with [writer] in [context] */
internal fun writePropertiesToJson(
    listOfGraphables: List<IsGraphable<*>>,
    writer: IsJsonLikeWriter,
    context: GraphContext?
) {
    val transformed = Graph.Properties.properties.toSerializable!!.invoke(listOfGraphables, context)!!

    writer.writeStartArray()
    for (graphable in transformed) {
        when (graphable.value) {
            is Graph<*, *> -> Graph.writeJson(
                graphable.value, writer, context
            )
            is IsPropertyReference<*, *> -> {
                writer.writeString(graphable.value.completeName)
            }
            else -> throw ParseException("Cannot write unknown graphType ${graphable.type}")
        }
    }
    writer.writeEndArray()
}
