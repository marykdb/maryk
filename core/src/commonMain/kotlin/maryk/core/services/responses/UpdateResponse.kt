package maryk.core.services.responses

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.addDataModel
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.UpdateResponseType
import maryk.core.query.responses.updates.mapOfUpdateResponses
import maryk.core.services.ServiceDataModel
import maryk.core.values.ObjectValues

/**
 * Received an update response
 * Contains an id to listener which sent this response.
 */
data class UpdateResponse<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val id: ULong,
    override val dataModel: DM,
    val update: IsUpdateResponse<DM, P>
): IsServiceResponse, IsDataModelResponse<DM> {
    object Properties : ObjectPropertyDefinitions<UpdateResponse<*, *>>() {
        val id by number(1u, UpdateResponse<*, *>::id, type = UInt64)
        val dataModel by addDataModel(UpdateResponse<*, *>::dataModel, 2u)
        val update by ObjectDefinitionWrapperDelegateLoader(this) { propName ->
            MultiTypeDefinitionWrapper(
                3u,
                propName,
                InternalMultiTypeDefinition(
                    typeEnum = UpdateResponseType,
                    definitionMap = mapOfUpdateResponses
                ),
                getter = UpdateResponse<*, *>::update,
                toSerializable = { value, _ ->
                    value?.let { TypedValue(value.type, value) }
                },
                fromSerializable = { it?.value }
            )
        }
    }

    companion object : ServiceDataModel<UpdateResponse<*, *>, Properties>(
        serviceClass = UpdateResponse::class,
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<UpdateResponse<*, *>, Properties>) =
            UpdateResponse<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                id = values(1u),
                dataModel = values(2u),
                update = values(3u)
            )
    }
}
