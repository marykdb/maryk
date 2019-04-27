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
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

typealias AnyInject = Inject<*, *>

@Suppress("FunctionName")
fun Inject(collectionName: String) = Inject<Any, IsPropertyDefinition<Any>>(collectionName, null)

/**
 * To inject a variable into a request
 */
data class Inject<T : Any, D : IsPropertyDefinition<T>>(
    internal val collectionName: String,
    internal val propertyReference: IsPropertyReference<T, D, *>?
) {
    /** Resolve a value to inject from [context] */
    fun resolve(context: RequestContext): T? {
        val result = context.retrieveResult(collectionName)
            ?: throw InjectException(this.collectionName)

        @Suppress("UNCHECKED_CAST")
        return propertyReference?.let {
            result[propertyReference]
        } ?: result as T?
    }

    internal object Properties : ObjectPropertyDefinitions<AnyInject>() {
        val collectionName = add(1u, "collectionName",
            definition = StringDefinition(),
            getter = Inject<*, *>::collectionName,
            capturer = { context: InjectionContext, value ->
                context.collectionName = value
            }
        )
        val propertyReference = add(
            2u, "propertyReference",
            ContextualPropertyReferenceDefinition { context: InjectionContext? ->
                context?.let {
                    context.resolvePropertyReference()
                } ?: throw ContextNotFoundException()
            },
            Inject<*, *>::propertyReference
        )
    }

    internal companion object : ContextualDataModel<AnyInject, Properties, RequestContext, InjectionContext>(
        properties = Properties,
        contextTransformer = { requestContext ->
            InjectionContext(
                requestContext ?: throw ContextNotFoundException()
            )
        }
    ) {
        override fun invoke(values: ObjectValues<AnyInject, Properties>) = Inject<Any, IsPropertyDefinition<Any>>(
            collectionName = values(1u),
            propertyReference = values(2u)
        )

        override fun writeJson(obj: AnyInject, writer: IsJsonLikeWriter, context: InjectionContext?) {
            if (obj.propertyReference != null) {
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

        override fun readJson(
            reader: IsJsonLikeReader,
            context: InjectionContext?
        ): ObjectValues<AnyInject, Properties> {
            if (reader.currentToken == StartDocument) {
                reader.nextToken()
            }

            return when (val startToken = reader.currentToken) {
                is StartObject -> {
                    val currentToken = reader.nextToken()

                    val collectionName = (currentToken as? FieldName)?.value
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
                is Value<*> -> {
                    val collectionName = startToken.value as? String
                        ?: throw ParseException("Expected a collectionName in an Inject")

                    Properties.collectionName.capture(context, collectionName)

                    reader.nextToken()

                    this.values(context?.requestContext) {
                        mapNonNulls(
                            Properties.collectionName withSerializable collectionName
                        )
                    }
                }
                else -> throw ParseException("JSON value for Inject should be an Object or String")
            }
        }
    }
}
