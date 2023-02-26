package maryk.core.query.responses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.UpdateResponseType
import maryk.core.query.responses.updates.mapOfUpdateResponses
import maryk.core.values.ObjectValues

/**
 * Received an update response
 * Contains an id to listener which sent this response.
 */
data class UpdateResponse<DM: IsRootModel>(
    override val dataModel: DM,
    val update: IsUpdateResponse<DM>
): IsDataResponse<DM>, IsDataModelResponse<DM>, IsStoreRequest<DM, ProcessResponse<DM>> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<UpdateResponse<*>>(
        properties = object : ObjectPropertyDefinitions<UpdateResponse<*>>() {
            val dataModel by addDataModel({ it.dataModel }, 1u)
            val update =
                MultiTypeDefinitionWrapper(
                    2u,
                    "update",
                    InternalMultiTypeDefinition(
                        typeEnum = UpdateResponseType,
                        definitionMap = mapOfUpdateResponses
                    ),
                    getter = UpdateResponse<*>::update,
                    toSerializable = { value, _ ->
                        value?.let { TypedValue(value.type, value) }
                    },
                    fromSerializable = { it?.value }
                ).also(::addSingle)
        }
    ) {
        override fun invoke(values: ObjectValues<UpdateResponse<*>, ObjectPropertyDefinitions<UpdateResponse<*>>>) =
            UpdateResponse(
                dataModel = values(1u),
                update = values(2u)
            )
    }
}
