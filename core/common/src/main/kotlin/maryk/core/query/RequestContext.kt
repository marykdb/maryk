package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.objects.AbstractValues
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

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

    private var collectedResults = mutableMapOf<String, AbstractValues<*, *, *>>()
    private var toCollect = mutableMapOf<String, IsDataModel<*>>()

    /** Collect result [values] by [collectionName] */
    fun collectResult(collectionName: String, values: AbstractValues<*, *, *>) {
        if (values.dataModel !== this.toCollect[collectionName]) {
            throw Exception("Collect($collectionName): Value $values is not of right dataModel ${toCollect[collectionName]} ")
        }
        this.collectedResults.put(collectionName, values)
    }

    /** Retrieve result values by [collectionName] */
    fun retrieveResult(collectionName: String) =
        this.collectedResults[collectionName]

    /** Add to collect values by [model] into [collectionName] */
    fun addToCollect(collectionName: String, model: IsDataModel<*>) {
        toCollect[collectionName] = model
    }

    /** Get model of to be collected value by [collectionName] */
    fun getToCollectModel(collectionName: String) = toCollect[collectionName]
}
