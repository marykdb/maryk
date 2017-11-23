package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.changes.DataObjectVersionedChange

/** Response with all versioned changes since version in request
 * @param dataModel from which response data was retrieved
 * @param changes which occurred since given start version
 */
data class ObjectVersionedChangesResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val changes: List<DataObjectVersionedChange<DO>>
) : IsDataModelResponse<DO, DM> {
    object Properties {
        val changes = ListDefinition(
                name = "changes",
                index = 1,
                required = true,
                valueDefinition = SubModelDefinition(
                        required = true,
                        dataModel = DataObjectVersionedChange
                )
        )
    }

    companion object: QueryDataModel<ObjectVersionedChangesResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ObjectVersionedChangesResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        changes = it[1] as List<DataObjectVersionedChange<Any>>
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, ObjectVersionedChangesResponse<*, *>::dataModel),
                    Def(Properties.changes, ObjectVersionedChangesResponse<*, *>::changes)
            )
    )
}
