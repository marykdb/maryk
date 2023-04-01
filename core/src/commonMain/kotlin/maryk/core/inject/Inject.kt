package maryk.core.inject

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ContextualModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.values
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

typealias AnyInject = Inject<*, *>

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

    internal companion object : ContextualModel<AnyInject, Companion, RequestContext, InjectionContext>(
        contextTransformer = { requestContext ->
            InjectionContext(
                requestContext ?: throw ContextNotFoundException()
            )
        }
    ) {
        val collectionName by string(
            1u,
            getter = Inject<*, *>::collectionName,
            capturer = { context: IsPropertyContext, value ->
                (context as InjectionContext).collectionName = value
            }
        )
        val propertyReference by contextual(
            index = 2u,
            getter = Inject<*, *>::propertyReference,
            definition = ContextualPropertyReferenceDefinition { context: InjectionContext? ->
                context?.let {
                    context.resolvePropertyReference()
                } ?: throw ContextNotFoundException()
            }
        )

        override fun invoke(values: ObjectValues<AnyInject, Companion>) = Inject<Any, IsPropertyDefinition<Any>>(
            collectionName = values(1u),
            propertyReference = values(2u)
        )

        override val Model: ContextualDataModel<AnyInject, Companion, RequestContext, InjectionContext> = object : ContextualDataModel<AnyInject, Companion, RequestContext, InjectionContext>(
            properties = Companion,
            contextTransformer = contextTransformer
        ) {
            override fun writeJson(obj: AnyInject, writer: IsJsonLikeWriter, context: InjectionContext?) {
                if (obj.propertyReference != null) {
                    writer.writeStartObject()
                    writer.writeFieldName(obj.collectionName)

                    propertyReference.writeJsonValue(
                        obj.propertyReference,
                        writer,
                        context
                    )

                    writer.writeEndObject()
                } else {
                    collectionName.writeJsonValue(obj.collectionName, writer, context)
                }
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: InjectionContext?
            ): ObjectValues<AnyInject, Companion> {
                if (reader.currentToken == JsonToken.StartDocument) {
                    reader.nextToken()
                }

                return when (val startToken = reader.currentToken) {
                    is JsonToken.StartObject -> {
                        val currentToken = reader.nextToken()

                        val collectionName = (currentToken as? JsonToken.FieldName)?.value
                            ?: throw ParseException("Expected a collectionName in an Inject")

                        Companion.collectionName.capture(context, collectionName)

                        reader.nextToken()
                        val propertyReference = Companion.propertyReference.readJson(reader, context)
                        Companion.propertyReference.capture(context, propertyReference)

                        reader.nextToken() // read past end object

                        this.properties.values(context?.requestContext) {
                            mapNonNulls(
                                Companion.collectionName withSerializable collectionName,
                                Companion.propertyReference withSerializable propertyReference
                            )
                        }
                    }
                    is JsonToken.Value<*> -> {
                        val collectionName = startToken.value as? String
                            ?: throw ParseException("Expected a collectionName in an Inject")

                        Companion.collectionName.capture(context, collectionName)

                        reader.nextToken()

                        this.properties.values(context?.requestContext) {
                            mapNonNulls(
                                Companion.collectionName withSerializable collectionName
                            )
                        }
                    }
                    else -> throw ParseException("JSON value for Inject should be an Object or String")
                }
            }
        }
    }
}
