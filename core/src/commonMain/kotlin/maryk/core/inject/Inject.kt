package maryk.core.inject

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

typealias AnyInject = Inject<*, *>

@Suppress("FunctionName")
fun Inject(collectionName: String) = Inject<Any, IsPropertyDefinition<Any>>(collectionName, null)

/**
 * To inject a variable into a request
 */
data class Inject<T: Any, D: IsPropertyDefinition<T>>(
    private val collectionName: String,
    private val propertyReference: IsPropertyReference<T, D, *>?
) {
    fun resolve(context: RequestContext): T? {
        val result = context.retrieveResult(collectionName)
            ?: throw InjectException(this.collectionName)

        @Suppress("UNCHECKED_CAST")
        return propertyReference?.let {
            result[propertyReference]
        } ?: result as T?
    }

    internal object Properties: ObjectPropertyDefinitions<AnyInject>() {
        val collectionName = add(1, "collectionName",
            definition = StringDefinition(),
            getter = Inject<*, *>::collectionName,
            capturer = { context: InjectionContext, value ->
                context.collectionName = value
            }
        )
        val propertyReference = add(2, "propertyReference",
            ContextualPropertyReferenceDefinition { context: InjectionContext? ->
                context?.let {
                    context.resolvePropertyReference()
                } ?: throw ContextNotFoundException()
            },
            Inject<*, *>::propertyReference
        )
    }

    internal companion object: ContextualDataModel<AnyInject, Properties, RequestContext, InjectionContext>(
        properties = Properties,
        contextTransformer = { requestContext ->
            InjectionContext(
                requestContext ?: throw ContextNotFoundException()
            )
        }
    ) {
        override fun invoke(values: ObjectValues<AnyInject, Properties>) = Inject<Any, IsPropertyDefinition<Any>>(
            collectionName = values(1),
            propertyReference = values(2)
        )

        override fun writeJson(obj: AnyInject, writer: IsJsonLikeWriter, context: InjectionContext?) {
            if (obj.propertyReference != null){
                writer.writeStartObject()
                writer.writeFieldName(obj.collectionName)

                Properties.propertyReference.writeJsonValue(
                    obj.propertyReference,
                    writer,
                    context
                )

                writer.writeEndObject()
            } else {
                Properties.collectionName.writeJsonValue(obj.collectionName, writer, context)
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: InjectionContext?): ObjectValues<AnyInject, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            val startToken = reader.currentToken

            return when (startToken) {
                is JsonToken.StartObject -> {
                    val currentToken = reader.nextToken()

                    val collectionName = (currentToken as? JsonToken.FieldName)?.value
                            ?: throw ParseException("Expected a collectionName in an Inject")

                    Properties.collectionName.capture(context, collectionName)

                    reader.nextToken()
                    val propertyReference = Properties.propertyReference.readJson(reader, context)
                    Properties.propertyReference.capture(context, propertyReference)

                    reader.nextToken() // read past end object

                    this.values(context?.requestContext) {
                        mapNonNulls(
                            Properties.collectionName withSerializable collectionName,
                            Properties.propertyReference withSerializable propertyReference
                        )
                    }
                }
                is JsonToken.Value<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val collectionName = (startToken as? JsonToken.Value<String>)?.value
                            ?: throw ParseException("Expected a collectionName in an Inject")

                    Properties.collectionName.capture(context, collectionName)

                    reader.nextToken()

                    this.values(context?.requestContext) {
                        mapNonNulls(
                            Properties.collectionName withSerializable collectionName
                        )
                    }
                }
                else -> {
                    throw ParseException("JSON value for Inject should be an Object or String")
                }
            }
        }
    }
}
