package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.listOfStatuses

/** Response to an Add request
 * @param dataModel to which objects were added
 * @param statuses of all specific add objects
 */
data class AddResponse<DO: Any, out DM: RootDataModel<DO>> constructor(
        override val dataModel: DM,
        val statuses: List<IsAddResponseStatus<DO>>
): IsDataModelResponse<DO, DM> {
    internal object Properties {
        val statuses = listOfStatuses
    }

    companion object: QueryDataModel<AddResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                AddResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        statuses = (it[1] as List<TypedValue<IsAddResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, AddResponse<*, *>::dataModel),
                    Def(Properties.statuses, { it.statuses.map { TypedValue(it.statusType.index, it) } })
            )
    )
}
