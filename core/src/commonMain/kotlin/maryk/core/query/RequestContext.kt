package maryk.core.query

import maryk.core.exceptions.RequestException
import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.requests.IsRequest
import maryk.core.query.responses.IsResponse
import maryk.core.values.AbstractValues

sealed class ModelTypeToCollect<DM : IsDataModel<*>>(val model: DM) {
    class Request<RP : IsResponse>(val request: IsRequest<RP>) :
        ModelTypeToCollect<IsObjectDataModel<RP, *>>(request.responseModel)

    class Model<DM : IsDataModel<*>>(value: DM) : ModelTypeToCollect<DM>(value)
}

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
class RequestContext(
    val definitionsContext: ContainsDefinitionsContext,
    override var dataModel: IsDataModel<*>? = null,
    var reference: IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>? = null
) : ContainsDataModelContext<IsDataModel<*>>, ContainsDefinitionsContext by definitionsContext {
    /** For test use */
    internal constructor(
        dataModels: Map<String, Unit.() -> IsNamedDataModel<*>>,
        dataModel: IsDataModel<*>? = null,
        reference: IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>? = null
    ) : this(
        DefinitionsContext(dataModels.toMutableMap()),
        dataModel,
        reference
    )

    private var toCollect = mutableMapOf<String, ModelTypeToCollect<*>>()
    private var collectedResults = mutableMapOf<String, AbstractValues<*, *, *>>()

    internal var collectedInjects = mutableListOf<InjectWithReference>()

    private var collectedLevels = mutableListOf<Any>()
    private var collectedReferenceCreators = mutableListOf<(AnyPropertyReference?) -> IsPropertyReference<Any, *, *>>()

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
        val toCollect = this.toCollect[collectionName]
            ?: throw RequestException("$collectionName was not defined as to collect in RequestContext")

        if (values.dataModel !== toCollect.model) {
            throw RequestException("Collect($collectionName): Value $values is not of right dataModel $toCollect ")
        }

        this.collectedResults[collectionName] = values
    }

    /** Retrieve result values by [collectionName] */
    fun retrieveResult(collectionName: String) =
        this.collectedResults[collectionName]

    /**
     * Collects Injects [inject] so they can be encoded in a higher Requests object
     * This because ProtoBuf does not allow encoding Injects in place
     */
    fun collectInject(
        inject: Inject<*, *>
    ) {
        var reference: IsPropertyReference<Any, *, *>? = null
        // Create the reference by walking all parent reference creators
        for (creator in this.collectedReferenceCreators) {
            reference = creator(reference)
        }

        this.collectedInjects.add(
            InjectWithReference(inject, reference!!)
        )
    }

    /** Collect the [propertyRefCreator] for the level defined by [levelItem] */
    fun collectInjectLevel(
        levelItem: Any,
        propertyRefCreator: (AnyPropertyReference?) -> IsPropertyReference<Any, *, *>
    ) {
        if (this.collectedLevels.isNotEmpty() && levelItem == this.collectedLevels.last()) {
            this.collectedReferenceCreators.removeAt(this.collectedReferenceCreators.size - 1)
        } else {
            this.collectedLevels.add(levelItem)
        }

        this.collectedReferenceCreators.add(propertyRefCreator)
    }

    /** Removes the last injection [levelItem] and its reference creator */
    fun closeInjectLevel(
        levelItem: Any
    ) {
        if (this.collectedLevels.isNotEmpty() && levelItem == this.collectedLevels.last()) {
            this.collectedLevels.removeAt(this.collectedLevels.size - 1)
            this.collectedReferenceCreators.dropLast(1)
        }
    }
}
