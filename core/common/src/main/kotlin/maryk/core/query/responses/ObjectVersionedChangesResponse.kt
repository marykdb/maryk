package maryk.core.query.responses

import maryk.core.models.RootObjectDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ObjectVersionedChangesResponse<DO: Any, out DM: RootObjectDataModel<DO, *>>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<ObjectVersionedChangesResponse<*, *>>(
        properties = object : ObjectPropertyDefinitions<ObjectVersionedChangesResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ObjectVersionedChangesResponse<*, *>::dataModel)
                add(1, "changes", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { DataObjectVersionedChange }
                    )
                ), ObjectVersionedChangesResponse<*, *>::changes)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ObjectVersionedChangesResponse<*, *>>) = ObjectVersionedChangesResponse(
            dataModel = map<RootObjectDataModel<Any, *>>(0),
            changes = map(1)
        )
    }
}
