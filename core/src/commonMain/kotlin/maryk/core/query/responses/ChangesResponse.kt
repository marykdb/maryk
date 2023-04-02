package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.values.SimpleObjectValues

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ChangesResponse<out DM : IsRootDataModel>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>
) : IsDataResponse<DM> {
    @Suppress("unused")
    companion object : SimpleQueryModel<ChangesResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val changes by list(
            index = 2u,
            getter = ChangesResponse<*>::changes,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { DataObjectVersionedChange }
            )
        )

        override fun invoke(values: SimpleObjectValues<ChangesResponse<*>>) = ChangesResponse(
            dataModel = values(1u),
            changes = values(2u)
        )
    }
}
