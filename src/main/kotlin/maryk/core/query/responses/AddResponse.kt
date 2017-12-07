package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus

/** Response to an Add request
 * @param dataModel to which objects were added
 * @param statuses of all specific add objects
 */
data class AddResponse<DO: Any, out DM: RootDataModel<DO, *>> constructor(
        override val dataModel: DM,
        val statuses: List<IsAddResponseStatus<DO>>
): IsDataModelResponse<DO, DM> {
    companion object: QueryDataModel<AddResponse<*, *>>(
            properties = object : PropertyDefinitions<AddResponse<*, *>>() {
                init {
                    IsDataModelResponse.addDataModel(this, AddResponse<*, *>::dataModel)
                    IsDataModelResponse.addStatuses(this) {
                        it.statuses.map { TypedValue(it.statusType.index, it) }
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = AddResponse(
                dataModel = map[0] as RootDataModel<Any, *>,
                statuses = (map[1] as List<TypedValue<IsAddResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
        )
    }
}
