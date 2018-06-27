package maryk.core.query.responses

import maryk.core.models.QueryDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.query.changes.DataObjectVersionedChange

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ObjectVersionedChangesResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: QueryDataModel<ObjectVersionedChangesResponse<*, *>>(
        properties = object : PropertyDefinitions<ObjectVersionedChangesResponse<*, *>>() {
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
        override fun invoke(map: Map<Int, *>) = ObjectVersionedChangesResponse(
            dataModel = map<RootDataModel<Any, *>>(0),
            changes = map(1)
        )
    }
}
