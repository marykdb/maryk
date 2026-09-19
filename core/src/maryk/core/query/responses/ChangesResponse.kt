package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.values.SimpleObjectValues

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ChangesResponse<out DM : IsRootDataModel>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>,
    val dataFetchType: DataFetchType? = null,
) : IsDataResponse<DM> {
    companion object : SimpleQueryModel<ChangesResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val changes by list(
            index = 2u,
            getter = ChangesResponse<*>::changes,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { DataObjectVersionedChange }
            )
        )
        internal val dataFetchType = MultiTypeDefinitionWrapper(
            3u,
            "dataFetchType",
            MultiTypeDefinition(required = false, typeEnum = DataFetchTypeType),
            getter = ChangesResponse<*>::dataFetchType,
            toSerializable = { value, _ -> value?.let { TypedValue(it.type, it) } },
            fromSerializable = { it?.value },
        ).also(::addSingle)

        override fun invoke(values: SimpleObjectValues<ChangesResponse<*>>) = ChangesResponse(
            dataModel = values(dataModel.index),
            changes = values(changes.index),
            dataFetchType = values(dataFetchType.index),
        )
    }
}
