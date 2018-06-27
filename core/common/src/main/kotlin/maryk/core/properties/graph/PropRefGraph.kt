package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.objects.DataObjectMap
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

/** Get a graph for property references of an embedded object */
fun <PDO: Any, DO: Any> IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>.graph(
    vararg property: IsPropRefGraphable<DO>
) = PropRefGraph(this, property.toList())

/**
 * Represents a Property Reference Graph branch below a [parent] with all [properties] to fetch
 */
data class PropRefGraph<PDO: Any, DO: Any> internal constructor(
    val parent: IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>,
    val properties: List<IsPropRefGraphable<DO>>
) : IsPropRefGraphable<PDO> {
    override val graphType = PropRefGraphType.Graph

    internal object Properties : PropertyDefinitions<PropRefGraph<*, *>>() {
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
            getter = PropRefGraph<*, *>::parent
        )

        val properties = addProperties(1, PropRefGraph<*, *>::properties) { context: GraphContext? ->
            context?.subDataModel?.properties ?: throw ContextNotFoundException()
        }
    }

    internal companion object : ContextualDataModel<PropRefGraph<*, *>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = PropRefGraph<Any, Any>(
            parent = map(0),
            properties = map(1)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: GraphContext?) {
            val reference = map[Properties.parent.index] as IsPropertyReference<*, *>
            @Suppress("UNCHECKED_CAST")
            val listOfGraphables = map[Properties.properties.index] as List<IsPropRefGraphable<*>>

            writeJsonValues(reference, listOfGraphables, writer, context)
        }

        override fun writeJson(obj: PropRefGraph<*, *>, writer: IsJsonLikeWriter, context: GraphContext?) {
            writeJsonValues(obj.parent.getRef(), obj.properties, writer, context)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun writeJsonValues(
            reference: IsPropertyReference<*, *>,
            listOfPropRefGraphables: List<IsPropRefGraphable<*>>,
            writer: IsJsonLikeWriter,
            context: GraphContext?
        ) {
            Properties.parent.capture(context, reference)

            writer.writeStartObject()
            writer.writeFieldName(reference.completeName)
            writePropertiesToJson(listOfPropRefGraphables, writer, context)

            writer.writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: GraphContext?): DataObjectMap<PropRefGraph<*, *>> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("JSON value should be an Object")
            }

            reader.nextToken()

            val parent = reader.currentToken.let {
                if (it !is JsonToken.FieldName) {
                    throw ParseException("JSON value should be a FieldName")
                }

                val value = it.value ?: throw ParseException("JSON value should not be null")

                Properties.parent.definition.fromString(value, context)
            }
            Properties.parent.capture(context, parent)

            if (reader.nextToken() !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val properties = mutableListOf<TypedValue<PropRefGraphType,*>>()

            while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                when (currentToken) {
                    is JsonToken.StartObject -> {
                        val newContext = PropRefGraph.transformContext(context)

                        properties.add(
                            TypedValue(
                                PropRefGraphType.Graph,
                                PropRefGraph.readJson(reader, newContext).toDataObject()
                            )
                        )
                    }
                    is JsonToken.Value<*> -> {
                        val multiTypeDefinition = Properties.properties.valueDefinition as MultiTypeDefinition<PropRefGraphType, GraphContext>

                        properties.add(
                            TypedValue(
                                PropRefGraphType.PropRef,
                                multiTypeDefinition.definitionMap[PropRefGraphType.PropRef]!!
                                    .readJson(reader, context) as IsPropertyReference<*, *>
                            )
                        )
                    }
                    else -> throw ParseException("JSON value should be a String or an Object")
                }

                currentToken = reader.nextToken()
            }

            reader.nextToken()

            return DataObjectMap(
                this,
                mapOf(
                    Properties.parent.index to parent,
                    Properties.properties.index to properties
                )
            )
        }
    }
}

/**
 * Add properties to Property Reference PropRefGraph objects so they are encodable
 */
internal fun <DO: Any> PropertyDefinitions<DO>.addProperties(
    index: Int,
    getter: (DO) -> List<IsPropRefGraphable<*>>,
    contextResolver: (GraphContext?) -> PropertyDefinitions<*>
) =
    add(index, "properties",
        ListDefinition(
            valueDefinition = MultiTypeDefinition(
                definitionMap = mapOf(
                    PropRefGraphType.Graph to EmbeddedObjectDefinition(
                        dataModel = { PropRefGraph }
                    ),
                    PropRefGraphType.PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = contextResolver
                    )
                ),
                typeEnum = PropRefGraphType
            )
        ),
        toSerializable = { value: IsPropRefGraphable<*> ->
            value.let {
                when (it) {
                    is IsPropertyDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.getRef())
                    is PropRefGraph<*, *> -> TypedValue(it.graphType, it)
                    else -> throw ParseException("Unknown PropRefGraphType ${it.graphType}")
                }
            }
        },
        fromSerializable = { value: TypedValue<PropRefGraphType, *> ->
            when (value.value) {
                is IsPropertyReference<*, *> -> value.value.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>
                is PropRefGraph<*, *> -> value.value
                else -> throw ParseException("Unknown PropRefGraphType ${value.type}")
            }
        },
        getter = getter
    )

/** Write properties to JSON with [writer] in [context] */
internal fun writePropertiesToJson(
    listOfPropRefGraphables: List<IsPropRefGraphable<*>>,
    writer: IsJsonLikeWriter,
    context: GraphContext?
) {
    val transformed = PropRefGraph.Properties.properties.toSerializable!!.invoke(listOfPropRefGraphables, context)!!

    writer.writeStartArray()
    for (graphable in transformed) {
        when (graphable.value) {
            is PropRefGraph<*, *> -> PropRefGraph.writeJson(
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
