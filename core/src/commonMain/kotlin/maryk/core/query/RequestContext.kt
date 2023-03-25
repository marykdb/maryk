package maryk.core.query

import maryk.core.exceptions.RequestException
import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.changes.IncMapChange
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.responses.IsResponse
import maryk.core.values.AbstractValues

sealed class ModelTypeToCollect<DM : IsDataModel<*>>(val model: DM) {
    class Request<RP : IsResponse>(val request: IsTransportableRequest<RP>) :
        ModelTypeToCollect<IsObjectDataModel<in RP, *>>(request.responseModel.Model)

    class Model<DM : IsDataModel<*>>(value: DM) : ModelTypeToCollect<DM>(value)
}

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
class RequestContext(
    val definitionsContext: ContainsDefinitionsContext,
    override var dataModel: IsPropertyDefinitions? = null,
    var reference: IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>? = null
) : ContainsDataModelContext<IsPropertyDefinitions>, ContainsDefinitionsContext by definitionsContext {
    /** For test use */
    internal constructor(
        dataModels: Map<String, Unit.() -> IsNamedDataModel<*>>,
        dataModel: IsPropertyDefinitions? = null,
        reference: IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>? = null
    ) : this(
        DefinitionsContext(dataModels.toMutableMap()),
        dataModel,
        reference
    )

    private var toCollect: MutableMap<String, ModelTypeToCollect<*>>? = null
    private var collectedResults: MutableMap<String, AbstractValues<*, *>>? = null

    internal var collectedInjects: MutableList<InjectWithReference>? = null

    private var collectedLevels: MutableList<Any>? = null
    private var collectedReferenceCreators: MutableList<(AnyPropertyReference?) -> IsPropertyReference<Any, *, *>>? = null

    private var collectedIncMapChanges: MutableList<IncMapChange>? = null

    /** Add to collect values by [model] into [collectionName] */
    fun addToCollect(collectionName: String, model: IsDataModel<*>) {
        if (toCollect == null) {
            toCollect = mutableMapOf()
        }
        toCollect!![collectionName] = ModelTypeToCollect.Model(model)
    }

    /** Add to collect values by [model] into [collectionName] */
    fun addToCollect(collectionName: String, model: IsTransportableRequest<*>) {
        if (toCollect == null) {
            toCollect = mutableMapOf()
        }
        toCollect!![collectionName] = ModelTypeToCollect.Request(model)
    }

    /** Get model of to be collected value by [collectionName] */
    fun getToCollectModel(collectionName: String) = toCollect?.get(collectionName)

    /** Collect result [values] by [collectionName] */
    fun collectResult(collectionName: String, values: AbstractValues<*, *>) {
        val toCollect = toCollect?.get(collectionName)
            ?: throw RequestException("$collectionName was not defined as to collect in RequestContext")

        if (values.dataModel.Model !== toCollect.model) {
            throw RequestException("Collect($collectionName): Value $values is not of right dataModel $toCollect ")
        }

        if (this.collectedResults == null) {
            this.collectedResults = mutableMapOf()
        }
        this.collectedResults!![collectionName] = values
    }

    /** Retrieve result values by [collectionName] */
    fun retrieveResult(collectionName: String) =
        this.collectedResults?.get(collectionName)

    /**
     * Collects Injects [inject] so they can be encoded in a higher Requests object
     * This because ProtoBuf does not allow encoding Injects in place
     */
    fun collectInject(
        inject: Inject<*, *>
    ) {
        var reference: IsPropertyReference<Any, *, *>? = null

        if (this.collectedReferenceCreators != null) {
            // Create the reference by walking all parent reference creators
            for (creator in this.collectedReferenceCreators!!) {
                reference = creator(reference)
            }
        }

        if (this.collectedInjects == null) {
            this.collectedInjects = mutableListOf()
        }

        this.collectedInjects!!.add(
            InjectWithReference(inject, reference!!)
        )
    }

    /** Collect the [propertyRefCreator] for the level defined by [levelItem] */
    fun collectInjectLevel(
        levelItem: Any,
        propertyRefCreator: (AnyPropertyReference?) -> IsPropertyReference<in Any, *, *>
    ) {
        if (!this.collectedLevels.isNullOrEmpty() && levelItem == this.collectedLevels!!.last()) {
            this.collectedReferenceCreators?.removeAt(this.collectedReferenceCreators!!.size - 1)
        } else {
            if (this.collectedLevels == null) {
                this.collectedLevels = mutableListOf()
            }
            this.collectedLevels!!.add(levelItem)
        }

        if (this.collectedReferenceCreators == null) {
            this.collectedReferenceCreators = mutableListOf()
        }

        this.collectedReferenceCreators!!.add(propertyRefCreator)
    }

    /** Removes the last injection [levelItem] and its reference creator */
    fun closeInjectLevel(
        levelItem: Any
    ) {
        if (!this.collectedLevels.isNullOrEmpty() && levelItem == this.collectedLevels!!.last()) {
            this.collectedLevels!!.removeAt(this.collectedLevels!!.size - 1)
            this.collectedReferenceCreators?.dropLast(1)
        }
    }

    /** Collect [incMapChange] so IncMapAdditions can be resolved */
    fun collectIncMapChange(incMapChange: IncMapChange) {
        if(this.collectedIncMapChanges == null) {
            this.collectedIncMapChanges = mutableListOf()
        }

        if (!this.collectedIncMapChanges!!.contains(incMapChange)) {
            this.collectedIncMapChanges!!.add(incMapChange)
        }
    }

    /** Get all collected Incrementing Map Changes */
    fun getCollectedIncMapChanges(): List<IncMapChange> =
        this.collectedIncMapChanges ?: emptyList()
}
