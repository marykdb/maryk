package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
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
    internal object Properties : PropertyDefinitions<ChangeResponse<*, *>>() {
        val statuses = listOfStatuses
    }

    companion object: QueryDataModel<ChangeResponse<*, *>>(
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, ChangeResponse<*, *>::dataModel),
                    Def(Properties.statuses, { it.statuses.map { TypedValue(it.statusType.index, it) } })
            ),
            properties = object : PropertyDefinitions<ChangeResponse<*, *>>() {
                init {
                    IsDataModelResponse.addDataModel(this, ChangeResponse<*, *>::dataModel)
                    add(1, "statuses", listOfStatuses) {
                        it.statuses.map { TypedValue(it.statusType.index, it) }
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ChangeResponse(
                dataModel = map[0] as RootDataModel<Any>,
                statuses = (map[1] as List<TypedValue<IsChangeResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
        )
    }
}