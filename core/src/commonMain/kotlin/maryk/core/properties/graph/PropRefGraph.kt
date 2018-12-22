package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * To make graph with [runner] on Properties to return list of graphables
 * The graphables are sorted after generation so the PropRefGraph can be processed quicker.
 */
@Suppress("unused")
fun <P: PropertyDefinitions, DM: IsValuesDataModel<PS>, PS: PropertyDefinitions> P.graph(
    embed: EmbeddedValuesPropertyDefinitionWrapper<DM, PS, IsPropertyContext>,
    runner: PS.() -> List<IsPropRefGraphNode<PS>>
) = PropRefGraph<P, DM, PS>(embed, runner(embed.definition.dataModel.properties).sortedBy { it.index })

/**
 * Represents a Property Reference Graph branch below a [parent] with all [properties] to fetch
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class PropRefGraph<P: PropertyDefinitions, DM: IsValuesDataModel<PS>, PS: PropertyDefinitions> internal constructor(
    val parent: EmbeddedValuesPropertyDefinitionWrapper<DM, PS, IsPropertyContext>,
    override val properties: List<IsPropRefGraphNode<PS>>
) : IsPropRefGraphNode<P>, IsPropRefGraph<PS> {
    override val index = parent.index
    override val graphType = PropRefGraphType.Graph

    override fun toString(): String {
        var values = ""
        properties.forEach {
            if (values.isNotBlank()) values += ", "
            values += when (it) {
                is IsPropertyDefinitionWrapper<*, *, *, *> -> it.name
                is PropRefGraph<*, *, *> -> it.toString()
                else -> throw Exception("Unknown Graphable type")
            }
        }
        return "Graph { $values }"
    }

    object Properties : ObjectPropertyDefinitions<PropRefGraph<*, *, *>>() {
        val parent = add(1, "parent",
            ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    context?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                context.subDataModel = (value.propertyDefinition as EmbeddedValuesPropertyDefinitionWrapper<*, *, *>).dataModel
            },
            toSerializable = { value: IsPropertyDefinitionWrapper<*, *, *, *>?, _ ->
                value?.getRef()
            },
            fromSerializable = { value ->
                value?.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>?
            },
            getter = PropRefGraph<*, *, *>::parent
        )

        val properties = addProperties(2, PropRefGraph<*, *, *>::properties) { context: GraphContext? ->
            context?.subDataModel?.properties as? PropertyDefinitions? ?: throw ContextNotFoundException()
        }
    }

    companion object : ContextualDataModel<PropRefGraph<*, *, *>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        override fun invoke(values: ObjectValues<PropRefGraph<*, *, *>, Properties>) = PropRefGraph<PropertyDefinitions, IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            parent = values(1),
            properties = values(2)
        )

        override fun writeJson(obj: PropRefGraph<*, *, *>, writer: IsJsonLikeWriter, context: GraphContext?) {
            writeJsonValues(obj.parent.getRef(), obj.properties, writer, context)
        }

        @Suppress("UNUSED_PARAMETER")
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

        override fun readJson(reader: IsJsonLikeReader, context: GraphContext?): ObjectValues<PropRefGraph<*, *, *>, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("JSON value should be an Object")
            }

            reader.nextToken()

            val parentValue = reader.currentToken.let {
                if (it !is JsonToken.FieldName) {
                    throw ParseException("JSON value should be a FieldName")
                }

                val value = it.value ?: throw ParseException("JSON value should not be null")

                Properties.parent.definition.fromString(value, context)
            }
            Properties.parent.capture(context, parentValue)

            if (reader.nextToken() !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val propertiesValue = mutableListOf<TypedValue<PropRefGraphType,*>>()

            while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                when (currentToken) {
                    is JsonToken.StartObject -> {
                        val newContext = PropRefGraph.transformContext(context)

                        propertiesValue.add(
                            TypedValue(
                                PropRefGraphType.Graph,
                                PropRefGraph.readJson(reader, newContext).toDataObject()
                            )
                        )
                    }
                    is JsonToken.Value<*> -> {
                        val multiTypeDefinition = Properties.properties.valueDefinition as MultiTypeDefinition<PropRefGraphType, GraphContext>

                        propertiesValue.add(
                            TypedValue(
                                PropRefGraphType.PropRef,
                                multiTypeDefinition.definitionMap[PropRefGraphType.PropRef]!!
                                    .readJson(reader, context) as AnyPropertyReference
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

/**
 * Add properties to Property Reference PropRefGraph objects so they are encodable
 */
internal fun <DO: Any> ObjectPropertyDefinitions<DO>.addProperties(
    index: Int,
    getter: (DO) -> List<IsPropRefGraphNode<*>>,
    contextResolver: (GraphContext?) -> PropertyDefinitions
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
        toSerializable = { value: IsPropRefGraphNode<*> ->
            value.let {
                when (it) {
                    is IsPropertyDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.getRef())
                    is PropRefGraph<*, *, *> -> TypedValue(it.graphType, it)
                    else -> throw ParseException("Unknown PropRefGraphType ${it.graphType}")
                }
            }
        },
        fromSerializable = { value: TypedValue<PropRefGraphType, *> ->
            when (value.value) {
                is AnyPropertyReference -> value.value.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>
                is PropRefGraph<*, *, *> -> value.value
                else -> throw ParseException("Unknown PropRefGraphType ${value.type}")
            }
        },
        getter = getter
    )

/** Write properties to JSON with [writer] in [context] */
internal fun writePropertiesToJson(
    listOfPropRefGraphNodes: List<IsPropRefGraphNode<*>>,
    writer: IsJsonLikeWriter,
    context: GraphContext?
) {
    val transformed = PropRefGraph.Properties.properties.toSerializable!!.invoke(listOfPropRefGraphNodes, context)!!

    writer.writeStartArray()
    for (graphable in transformed) {
        when (graphable.value) {
            is PropRefGraph<*, *, *> -> PropRefGraph.writeJson(
                graphable.value, writer, context
            )
            is AnyPropertyReference -> {
                writer.writeString(graphable.value.completeName)
            }
            else -> throw ParseException("Cannot write unknown graphType ${graphable.type}")
        }
    }
    writer.writeEndArray()
}
