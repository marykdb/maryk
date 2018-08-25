package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.objects.AbstractValues
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.requests.IsRequest
import maryk.core.query.responses.IsResponse

sealed class ModelTypeToCollect<DM: IsDataModel<*>>(val model: DM) {
    class Request<RP: IsResponse>(val request: IsRequest<RP>): ModelTypeToCollect<IsObjectDataModel<RP, *>>(request.responseModel)
    class Model<DM: IsDataModel<*>>(value: DM): ModelTypeToCollect<DM>(value)
}

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
class RequestContext(
    val definitionsContext: ContainsDefinitionsContext,
    override var dataModel: IsDataModel<*>? = null,
    var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>, *>? = null
) : ContainsDataModelContext<IsDataModel<*>>, ContainsDefinitionsContext by definitionsContext {
    /** For test use */
    internal constructor(
        dataModels: Map<String, () -> IsNamedDataModel<*>>,
        dataModel: IsDataModel<*>? = null,
        reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>, *>? = null
    ) : this(
        DefinitionsContext(dataModels.toMutableMap()),
        dataModel,
        reference
    )

    private var toCollect = mutableMapOf<String, ModelTypeToCollect<*>>()
    private var collectedResults = mutableMapOf<String, AbstractValues<*, *, *>>()

    /** Add to collect values by [model] into [collectionName] */
    fun addToCollect(collectionName: String, model: IsDataModel<*>) {
        toCollect[collectionName] = ModelTypeToCollect.Model(model)
    }

    /** Add to collect values by [model] into [collectionName] */
    fun addToCollect(collectionName: String, model: IsRequest<*>) {
        toCollect[collectionName] = ModelTypeToCollect.Request(model)
    }

    /** Get model of to be collected value by [collectionName] */
    fun getToCollectModel(collectionName: String) = toCollect[collectionName]

    /** Collect result [values] by [collectionName] */
    fun collectResult(collectionName: String, values: AbstractValues<*, *, *>) {
        val toCollect = this.toCollect[collectionName] ?: throw Exception("$collectionName was not defined as to collect in RequestContext")

        if (values.dataModel !== toCollect.model) {
            throw Exception("Collect($collectionName): Value $values is not of right dataModel $toCollect ")
        }

        this.collectedResults[collectionName] = values
    }

    /** Retrieve result values by [collectionName] */
    fun retrieveResult(collectionName: String) =
        this.collectedResults[collectionName]
}
