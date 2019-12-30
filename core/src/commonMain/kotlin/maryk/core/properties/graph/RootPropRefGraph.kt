package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.graph.PropRefGraphType.Graph
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/**
 * Create a Root graph with references to [properties]
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class RootPropRefGraph<P : IsPropertyDefinitions> internal constructor(
    override val properties: List<IsPropRefGraphNode<P>>
) : IsPropRefGraph<P> {
    object Properties : ObjectPropertyDefinitions<RootPropRefGraph<*>>() {
        val properties = this.addProperties(1u, RootPropRefGraph<*>::properties) { context: GraphContext? ->
            context?.dataModel?.properties as? PropertyDefinitions? ?: throw ContextNotFoundException()
        }
    }

    override fun toString() = "RootPropRefGraph { ${renderPropsAsString()} }"

    companion object : ContextualDataModel<RootPropRefGraph<*>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            GraphContext(it?.dataModel)
        }
    ) {
        override fun invoke(values: ObjectValues<RootPropRefGraph<*>, Properties>) =
            RootPropRefGraph<PropertyDefinitions>(
                properties = values(1u)
            )

        override fun writeJson(obj: RootPropRefGraph<*>, writer: IsJsonLikeWriter, context: GraphContext?) {
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
        ): ObjectValues<RootPropRefGraph<*>, Properties> {
            if (reader.currentToken == StartDocument) {
                reader.nextToken()
            }

            if (reader.currentToken !is StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val propertiesList = mutableListOf<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>>()

            while (currentToken != EndArray && currentToken !is Stopped) {
                when (currentToken) {
                    is StartObject -> {
                        propertiesList.add(
                            TypedValue(
                                Graph,
                                PropRefGraph.readJson(reader, context).toDataObject()
                            )
                        )
                    }
                    is Value<*> -> {
                        val multiTypeDefinition =
                            Properties.properties.valueDefinition as IsMultiTypeDefinition<PropRefGraphType, IsTransportablePropRefGraphNode, GraphContext>

                        propertiesList.add(
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

            return this.values {
                mapNonNulls(
                    properties withSerializable propertiesList
                )
            }
        }
    }
}
