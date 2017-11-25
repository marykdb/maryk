package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.listOfStatuses

/** Response to an Change request
 * @param dataModel to which objects were changed
 * @param statuses of all specific change object actions
 */
data class ChangeResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val statuses: List<IsChangeResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    internal object Properties {
        val statuses = listOfStatuses
    }

    companion object: QueryDataModel<ChangeResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ChangeResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        statuses = (it[1] as List<TypedValue<IsChangeResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, ChangeResponse<*, *>::dataModel),
                    Def(Properties.statuses, { it.statuses.map { TypedValue(it.statusType.index, it) } })
            )
    )
}