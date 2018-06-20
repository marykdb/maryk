package maryk.core.objects.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Create a Root graph with [properties]
 */
data class RootGraph<DO> internal constructor(
    val properties: List<IsGraphable<DO>>
) {
    constructor(vararg property: IsGraphable<DO>) : this(property.toList())

    internal object Properties : PropertyDefinitions<RootGraph<*>>() {
        val properties = this.addProperties(0, RootGraph<*>::properties)  { context: GraphContext? ->
            context?.dataModel?.properties ?: throw ContextNotFoundException()
        }
    }

    internal companion object : ContextualDataModel<RootGraph<*>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            GraphContext(it?.dataModel)
        }
    ) {
        override fun invoke(map: Map<Int, *>) = RootGraph<Any>(
            properties = map(0)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: GraphContext?) {
            @Suppress("UNCHECKED_CAST")
            val listOfGraphables = map[Properties.properties.index] as List<IsGraphable<*>>

            this.writeJsonValues(listOfGraphables, writer, context)
        }

        override fun writeJson(obj: RootGraph<*>, writer: IsJsonLikeWriter, context: GraphContext?) {
            this.writeJsonValues(obj.properties, writer, context)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun writeJsonValues(
            listOfGraphables: List<IsGraphable<*>>,
            writer: IsJsonLikeWriter,
            context: GraphContext?
        ) {
            writePropertiesToJson(listOfGraphables, writer, context)
        }

        override fun readJson(reader: IsJsonLikeReader, context: GraphContext?): Map<Int, Any> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }

            var currentToken = reader.nextToken()

            val properties = mutableListOf<TypedValue<GraphType, *>>()

            while (currentToken != JsonToken.EndArray && currentToken !is JsonToken.Stopped) {
                when (currentToken) {
                    is JsonToken.StartObject -> {
                        properties.add(
                            TypedValue(
                                GraphType.Graph,
                                Graph.readJsonToObject(reader, context)
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
                Properties.properties.index to properties
            )
        }
    }
}
