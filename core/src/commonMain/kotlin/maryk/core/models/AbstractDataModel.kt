package maryk.core.models

import maryk.core.inject.Inject
import maryk.core.models.serializers.IsJsonSerializer
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsTypedPropertyDefinitions
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.values
import maryk.core.query.RequestContext
import maryk.core.values.AbstractValues
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.Values
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.json.JsonWriter
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

typealias SimpleValuesDataModel<DM> = AbstractDataModel<Any, DM, Values<DM>, IsPropertyContext, IsPropertyContext>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractDataModel<DO : Any, DM : IsTypedPropertyDefinitions<DO>, V : AbstractValues<DO, DM>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    final override val properties: DM
) : IsDataModel<DM>, IsJsonSerializer<V, CX> {
    /**
     * Write [values] for this ObjectDataModel to JSON
     * Optionally pass a [context] when needed for more complex property types
     */
    override fun writeJson(
        values: V,
        context: CX?,
        pretty: Boolean
    ) = buildString {
        val writer = JsonWriter(pretty = pretty, ::append)
        writeJson(values, writer, context)
    }

    /**
     * Write [values] for this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    override fun writeJson(
        values: V,
        writer: IsJsonLikeWriter,
        context: CX?
    ) {
        writer.writeStartObject()
        for ((index, value) in values) {
            val definition = properties[index] ?: continue

            if (value is Inject<*, *>) {
                if (writer is YamlWriter) {
                    writer.writeFieldName(definition.name)
                    writer.writeTag("!:Inject")
                } else {
                    writer.writeFieldName("?${definition.name}")
                }

                val injectionContext = Inject.Serializer.transformContext(context as RequestContext)
                Inject.Model.writeJson(value, writer, injectionContext)
            } else {
                definition.capture(context, value)
                writeJsonValue(definition, writer, value, context)
            }
        }
        writer.writeEndObject()
    }

    internal fun writeJsonValue(
        def: IsDefinitionWrapper<in Any, in Any, IsPropertyContext, DO>,
        writer: IsJsonLikeWriter,
        value: Any,
        context: CX?
    ) {
        writer.writeFieldName(def.name)
        def.definition.writeJsonValue(value, writer, context)
    }

    /**
     * Read JSON from [json] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    override fun readJson(json: String, context: CX?): V {
        var i = 0
        val reader = JsonReader { json[i++] }
        return this.readJson(reader, context)
    }

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    override fun readJson(reader: IsJsonLikeReader, context: CX?): V =
        createValues(context, readJsonToMap(reader, context))

    /**
     * Read JSON from [reader] to a Map
     * Optionally pass a [context] when needed to read more complex property types
     */
    open fun readJsonToMap(reader: IsJsonLikeReader, context: CX? = null): MutableValueItems {
        if (reader.currentToken == StartDocument) {
            reader.nextToken()
        }

        return if (properties.isNotEmpty()) {
            if (reader.currentToken !is StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON, not ${reader.currentToken}")
            }

            val valueMap = MutableValueItems()
            reader.nextToken()
            walkJsonToRead(reader, valueMap, context)

            valueMap
        } else {
            reader.nextToken()
            MutableValueItems()
        }
    }

    internal open fun walkJsonToRead(
        reader: IsJsonLikeReader,
        values: MutableValueItems,
        context: CX?
    ) {
        walker@ do {
            val token = reader.currentToken
            when (token) {
                is FieldName -> {
                    var isInject = false

                    val fieldName = token.value?.let {
                        if (reader is JsonReader && it.startsWith("?")) {
                            isInject = true
                            it.substring(1)
                        } else {
                            it
                        }
                    } ?: throw ParseException("Empty field name not allowed in JSON")

                    val definition = properties[fieldName]
                    if (definition == null) {
                        reader.skipUntilNextField()
                        continue@walker
                    } else {
                        reader.nextToken()

                        // Skip null values
                        val valueToken = reader.currentToken as? Value<*>
                        if (valueToken != null && valueToken.value == null) {
                            reader.nextToken()
                            continue@walker
                        }

                        if (reader is IsYamlReader) {
                            reader.currentToken.let { yamlToken ->
                                if (yamlToken is TokenWithType) {
                                    yamlToken.type.let {
                                        if (it is UnknownYamlTag) {
                                            isInject = it.name == ":Inject"
                                        }
                                    }
                                }
                            }
                        }

                        if (isInject) {
                            val inject = Inject.Model.readJson(reader, Inject.Serializer.transformContext(context as RequestContext))

                            values[definition.index] = inject
                        } else {
                            val readValue = if (definition is IsEmbeddedObjectDefinition<*, *, *, *>) {
                                @Suppress("UNCHECKED_CAST")
                                (definition as IsEmbeddedObjectDefinition<*, *, CX, *>).readJsonToValues(
                                    reader,
                                    context
                                )
                            } else {
                                definition.readJson(reader, context)
                            }

                            values[definition.index] = readValue
                            definition.capture(context, readValue)
                        }
                    }
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is Stopped)
    }

    /** Creates Values [V] from a [items] map */
    internal open fun createValues(context: CX?, items: IsValueItems): V {
        @Suppress("UNCHECKED_CAST")
        return when (this.properties) {
            is IsObjectPropertyDefinitions<*> ->
                (this.properties as IsObjectPropertyDefinitions<Any>).values(context as? RequestContext) {
                    items
                } as V
            is IsValuesPropertyDefinitions ->
                this.properties.values(context as? RequestContext) {
                    items
                } as V
            else -> throw Exception("Unknown properties type ${this.properties::class.simpleName}")
        }
    }
}
