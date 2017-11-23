package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.changes.DataObjectChange

/** Response with all changes since version in request
 * @param dataModel from which response data was retrieved
 * @param changes which occurred since given start version
 */
data class ObjectChangesResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val changes: List<DataObjectChange<DO>>
) : IsDataModelResponse<DO, DM> {
    object Properties {
        val changes = ListDefinition(
                name = "changes",
                index = 1,
                required = true,
                valueDefinition = SubModelDefinition(
                        required = true,
                        dataModel = DataObjectChange
                )
        )
    }

    companion object: QueryDataModel<ObjectChangesResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ObjectChangesResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        changes = it[1] as List<DataObjectChange<Any>>
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, ObjectChangesResponse<*, *>::dataModel),
                    Def(Properties.changes, ObjectChangesResponse<*, *>::changes)
            )
    )
}