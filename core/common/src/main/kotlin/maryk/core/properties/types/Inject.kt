package maryk.core.properties.types

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ModelTypeToCollect
import maryk.core.query.RequestContext

typealias AnyInject = Inject<*, *>

/**
 * To inject a variable into a request
 */
data class Inject<T: Any, D: IsPropertyDefinition<T>>(
    private val collectionName: String,
    private val propertyReference: IsPropertyReference<T, D, *>? = null
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
        init {
            add(1, "collectionName",
                definition = StringDefinition(),
                getter = Inject<*, *>::collectionName,
                capturer = { context: InjectionContext, value ->
                    context.collectionName = value
                }
            )
            add(2, "propertyReference",
                ContextualPropertyReferenceDefinition { context: InjectionContext? ->
                    context?.let {
                        context.resolvePropertyReference()
                    } ?: throw ContextNotFoundException()
                },
                Inject<*, *>::propertyReference
            )
        }
    }

    internal companion object: ContextualDataModel<AnyInject, Properties, RequestContext, InjectionContext>(
        properties = Properties,
        contextTransformer = { requestContext -> InjectionContext(requestContext) }
    ) {
        override fun invoke(map: ObjectValues<AnyInject, Properties>) = Inject<Any, IsPropertyDefinition<Any>>(
            collectionName = map(1),
            propertyReference = map(2)
        )
    }
}

/** Context to resolve Inject properties */
internal class InjectionContext(
    private val requestContext: RequestContext?
): IsPropertyContext {
    fun resolvePropertyReference(): IsPropertyDefinitions? {
        collectionName?.let { collectionName ->
            val collectType = requestContext?.getToCollectModel(collectionName)

            return when(collectType) {
                null -> throw Exception("Inject collection name $collectionName not found")
                is ModelTypeToCollect.Request<*> -> {
                    collectType.model.properties
                }
                is ModelTypeToCollect.Model<*> -> {
                    collectType.model.properties
                }
            }
        } ?: throw ContextNotFoundException()
    }

    var collectionName: String? = null
}
