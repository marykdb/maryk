package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.responses.ChangeResponse

/**
 * Creates a request to change DataObjects with [objectChanges] in a Store.
 */
fun <DM: IsRootValuesDataModel<*>> DM.change(vararg objectChanges: DataObjectChange<DM>) =
    ChangeRequest(this, objectChanges.toList())

/** A Request to change DataObjects for [dataModel] with [objectChanges] */
data class ChangeRequest<DM: IsRootValuesDataModel<*>> internal constructor(
    override val dataModel: DM,
    val objectChanges: List<DataObjectChange<DM>>
) : IsStoreRequest<DM, ChangeResponse<DM>> {
    override val requestType = RequestType.Change
    @Suppress("UNCHECKED_CAST")
    override val responseModel = ChangeResponse as IsObjectDataModel<ChangeResponse<DM>, *>

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ChangeRequest<*>>() {
        val dataModel = IsObjectRequest.addDataModel(this, ChangeRequest<*>::dataModel)
        val objectChanges = add(2, "objectChanges",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { DataObjectChange }
                )
            ),
            getter = ChangeRequest<*>::objectChanges
        )
    }

    companion object: QueryDataModel<ChangeRequest<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ChangeRequest<*>, Properties>) = ChangeRequest(
            dataModel = map(1),
            objectChanges = map(2)
        )
    }
}
