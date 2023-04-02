package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.responses.updates.UpdateResponseType.InitialChanges
import maryk.core.values.SimpleObjectValues

/** Update containing the initial changes for listeners which listen to a scan changes. */
data class InitialChangesUpdate<DM: IsRootDataModel>(
    override val version: ULong,
    val changes: List<DataObjectVersionedChange<DM>>
): IsUpdateResponse<DM> {
    override val type = InitialChanges

    companion object : SimpleQueryModel<InitialChangesUpdate<*>>() {
        val version by number(1u, getter = InitialChangesUpdate<*>::version, type = UInt64)
        val changes by list(
            index = 2u,
            getter = InitialChangesUpdate<*>::changes,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { DataObjectVersionedChange }
            )
        )

        override fun invoke(values: SimpleObjectValues<InitialChangesUpdate<*>>) = InitialChangesUpdate<IsRootDataModel>(
            version = values(1u),
            changes = values(2u)
        )
    }
}
