package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.requests.RequestType.Change
import maryk.core.query.responses.ChangeResponse
import maryk.core.values.ObjectValues

/**
 * Creates a request to change DataObjects with [objectChanges] in a Store.
 */
fun <DM : IsRootDataModel<*>> DM.change(vararg objectChanges: DataObjectChange<DM>) =
    ChangeRequest(this, objectChanges.toList())

/** A Request to change DataObjects for [dataModel] with [objects] */
data class ChangeRequest<DM : IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    val objects: List<DataObjectChange<DM>>
) : IsStoreRequest<DM, ChangeResponse<DM>>, IsTransportableRequest<ChangeResponse<DM>> {
    override val requestType = Change
    override val responseModel = ChangeResponse

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ChangeRequest<*>>() {
        val to by addDataModel(ChangeRequest<*>::dataModel)
        val objects by list(
            index = 2u,
            getter = ChangeRequest<*>::objects,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { DataObjectChange }
            )
        )
    }

    companion object : QueryDataModel<ChangeRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ChangeRequest<*>, Properties>) = ChangeRequest(
            dataModel = values(1u),
            objects = values(2u)
        )
    }
}
