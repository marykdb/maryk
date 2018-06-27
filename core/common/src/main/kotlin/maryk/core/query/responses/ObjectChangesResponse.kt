package maryk.core.query.responses

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.changes.DataObjectChange

/** Response with all [changes] since version in request to [dataModel] */
data class ObjectChangesResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val changes: List<DataObjectChange<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<ObjectChangesResponse<*, *>>(
        properties = object : PropertyDefinitions<ObjectChangesResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ObjectChangesResponse<*, *>::dataModel)
                add(1, "changes", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { DataObjectChange }
                    )
                ), ObjectChangesResponse<*, *>::changes)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = ObjectChangesResponse(
            dataModel = map<RootDataModel<Any, *>>(0),
            changes = map(1)
        )
    }
}
