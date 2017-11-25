package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.listOfStatuses

/** Response to an Delete request
 * @param dataModel to which objects were added
 * @param statuses of all specific delete object actions
 */
data class DeleteResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val statuses: List<IsDeleteResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    internal object Properties {
        val statuses = listOfStatuses
    }

    companion object: QueryDataModel<DeleteResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                DeleteResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        statuses = (it[1] as List<TypedValue<IsDeleteResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, DeleteResponse<*, *>::dataModel),
                    Def(Properties.statuses, { it.statuses.map { TypedValue(it.statusType.index, it) } })
            )
    )
}
