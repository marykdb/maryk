package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.changes.DataObjectVersionedChange

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class VersionedChangesResponse<out DM: IsRootDataModel<*>>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>
) : IsDataModelResponse<DM> {
    companion object: SimpleQueryDataModel<VersionedChangesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<VersionedChangesResponse<*>>() {
            init {
                IsDataModelResponse.addDataModel(this, VersionedChangesResponse<*>::dataModel)
                add(2, "changes", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { DataObjectVersionedChange }
                    )
                ), VersionedChangesResponse<*>::changes)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<VersionedChangesResponse<*>>) = VersionedChangesResponse(
            dataModel = map(1),
            changes = map(2)
        )
    }
}
