package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.values.SimpleObjectValues

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ChangesResponse<out DM : IsRootDataModel<P>, P: IsValuesPropertyDefinitions>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>
) : IsDataResponse<DM, P> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<ChangesResponse<*, *>>(
        properties = object : ObjectPropertyDefinitions<ChangesResponse<*, *>>() {
            val dataModel by addDataModel(ChangesResponse<*, *>::dataModel)
            val changes by list(
                index = 2u,
                getter = ChangesResponse<*, *>::changes,
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { DataObjectVersionedChange }
                )
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangesResponse<*, *>>) = ChangesResponse(
            dataModel = values<IsRootDataModel<IsValuesPropertyDefinitions>>(1u),
            changes = values(2u)
        )
    }
}
