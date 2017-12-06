package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsChangeResponseStatus

/** Response to an Change request
 * @param dataModel to which objects were changed
 * @param statuses of all specific change object actions
 */
data class ChangeResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val statuses: List<IsChangeResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    companion object: QueryDataModel<ChangeResponse<*, *>>(
            properties = object : PropertyDefinitions<ChangeResponse<*, *>>() {
                init {
                    IsDataModelResponse.addDataModel(this, ChangeResponse<*, *>::dataModel)
                    IsDataModelResponse.addStatuses(this) {
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