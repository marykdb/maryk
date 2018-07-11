package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Create a Root graph with references to [properties]
 */
data class RootPropRefGraph<DO> internal constructor(
    val properties: List<IsPropRefGraphable<DO>>
) {
    constructor(vararg property: IsPropRefGraphable<DO>) : this(property.toList())

    internal object Properties : ObjectPropertyDefinitions<RootPropRefGraph<*>>() {
        val properties = this.addProperties(0, RootPropRefGraph<*>::properties)  { context: GraphContext? ->
            context?.dataModel?.properties as? ObjectPropertyDefinitions<*>? ?: throw ContextNotFoundException()
        }
    }

    internal companion object : ContextualDataModel<RootPropRefGraph<*>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            GraphContext(it?.dataModel)
        }
    ) {
        override fun invoke(map: Values<RootPropRefGraph<*>, Properties>) = RootPropRefGraph<Any>(
            properties = map(0)
        )

        override fun writeJson(map: Values<RootPropRefGraph<*>, Properties>, writer: IsJsonLikeWriter, context: GraphContext?) {
            @Suppress("UNCHECKED_CAST")
            val listOfGraphables = map { properties } ?: throw ParseException("RootPropRefGraph needs items in graph")

            writeJsonValues(listOfGraphables, writer, context)
        }

        override fun writeJson(obj: RootPropRefGraph<*>, writer: IsJsonLikeWriter, context: GraphContext?) {
            writeJsonValues(obj.properties, writer, context)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun writeJsonValues(
            listOfPropRefGraphables: List<IsPropRefGraphable<*>>,
            writer: IsJsonLikeWriter,
            context: GraphContext?
        ) {
            writePropertiesToJson(listOfPropRefGraphables, writer, context)
        }

        override fun readJson(reader: IsJsonLikeReader, context: GraphContext?): Values<RootPropRefGraph<*>, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val propertiesList = mutableListOf<TypedValue<PropRefGraphType, *>>()

            while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                when (currentToken) {
                    is JsonToken.StartObject -> {
                        propertiesList.add(
                            TypedValue(
                                PropRefGraphType.Graph,
                                PropRefGraph.readJson(reader, context).toDataObject()
                            )
                        )
                    }
                    is JsonToken.Value<*> -> {
                        val multiTypeDefinition = Properties.properties.valueDefinition as MultiTypeDefinition<PropRefGraphType, GraphContext>

                        propertiesList.add(
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

            return this.map {
                mapOf(
                    properties with propertiesList
                )
            }
        }
    }
}
