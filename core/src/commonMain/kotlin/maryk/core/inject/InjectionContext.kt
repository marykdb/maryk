package maryk.core.inject

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.query.ContainsDataModelContext
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.ModelTypeToCollect
import maryk.core.query.RequestContext
import maryk.core.query.requests.IsObjectRequest

/** Context to resolve Inject properties */
internal class InjectionContext(
    val requestContext: RequestContext
) :
    IsPropertyContext,
    ContainsDataModelContext<IsDataModel<*>>,
    ContainsDefinitionsContext by requestContext {
    var collectionName: String? = null

    override val dataModel: IsDataModel<*>
        get() = collectionName?.let { collectionName ->
            return when (val collectType = requestContext.getToCollectModel(collectionName)) {
                null -> throw RequestException("Inject collection name $collectionName not found")
                is ModelTypeToCollect.Request<*> -> {
                    if (collectType.request is IsObjectRequest<*, *>) {
                        collectType.request.dataModel
                    } else {
                        collectType.request.responseModel
                    }
                }
                is ModelTypeToCollect.Model<*> -> {
                    collectType.model
                }
            }
        } ?: throw ContextNotFoundException()

    fun resolvePropertyReference(): IsPropertyDefinitions =
        collectionName?.let { collectionName ->
            when (val collectType = requestContext.getToCollectModel(collectionName)) {
                null -> throw RequestException("Inject collection name $collectionName not found")
                is ModelTypeToCollect.Request<*> -> {
                    collectType.model.properties
                }
                is ModelTypeToCollect.Model<*> -> {
                    collectType.model.properties
                }
            }
        } ?: throw ContextNotFoundException()
}
