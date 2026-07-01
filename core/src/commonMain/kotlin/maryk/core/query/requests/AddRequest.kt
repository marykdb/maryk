package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.requests.RequestType.Add
import maryk.core.query.responses.AddResponse
import maryk.core.values.ObjectValues
import maryk.core.values.Values

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM : IsRootDataModel> DM.add(vararg objectToAdd: Values<DM>) =
    AddRequest(this, objectToAdd.toList())

/** Use only for mock or predefined data. */
fun <DM : IsRootDataModel> DM.add(vararg objectToAdd: Pair<Key<DM>, Values<DM>>) =
    AddRequest(this, objectToAdd.map { it.second }, objectToAdd.map { it.first })

/** A Request to add [objects] to [dataModel]. Use Keys only for predefined data */
data class AddRequest<DM : IsRootDataModel> internal constructor(
    override val dataModel: DM,
    val objects: List<Values<DM>>,
    val keysForObjects: List<Key<DM>>? = null,
) : IsStoreRequest<DM, AddResponse<DM>>, IsTransportableRequest<AddResponse<DM>> {
    override val requestType = Add
    override val responseModel = AddResponse

    init {
        if (objects.size.toUInt() > MAX_REQUEST_BATCH_SIZE) {
            throw RequestException("Add object count ${objects.size} exceeds maximum $MAX_REQUEST_BATCH_SIZE")
        }
        if (keysForObjects != null && keysForObjects.size != objects.size) {
            throw RequestException("Add key count ${keysForObjects.size} should equal object count ${objects.size}")
        }
    }

    companion object : QueryModel<AddRequest<*>, Companion>() {
        val to by addDataModel { it.dataModel }

        val objects by list(
            index = 2u,
            getter = AddRequest<*>::objects,
            maxSize = MAX_REQUEST_BATCH_SIZE,
            valueDefinition = ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    @Suppress("UNCHECKED_CAST")
                    it?.dataModel as? TypedValuesDataModel<IsValuesDataModel>
                        ?: throw ContextNotFoundException()
                }
            )
        )
        val keysForObjects by list(
            index = 3u,
            getter = AddRequest<*>::keysForObjects,
            maxSize = MAX_REQUEST_BATCH_SIZE,
            valueDefinition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? IsRootDataModel ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<AddRequest<*>, Companion>) =
            AddRequest(
                dataModel = values(1u),
                objects = values(2u),
                keysForObjects = values(3u)
            )
    }
}
