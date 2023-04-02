package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.UpdateResponseType
import maryk.core.query.responses.updates.mapOfUpdateResponses
import maryk.core.values.SimpleObjectValues

/** Response with [updates] to [dataModel] */
data class UpdatesResponse<DM : IsRootDataModel>(
    override val dataModel: DM,
    val updates: List<IsUpdateResponse<DM>>
) : IsDataResponse<DM> {
    companion object : SimpleQueryModel<UpdatesResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val updates by list(
            index = 2u,
            getter = UpdatesResponse<*>::updates,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = UpdateResponseType,
                definitionMap = mapOfUpdateResponses
            ),
            toSerializable = { TypedValue(it.type, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: SimpleObjectValues<UpdatesResponse<*>>) = UpdatesResponse(
            dataModel = values(1u),
            updates = values(2u)
        )
    }
}
