package maryk.core.query.responses.updates

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.responses.updates.UpdateResponseType.InitialChanges
import maryk.core.values.SimpleObjectValues

/** Update containing the initial changes for listeners which listen to a scan changes. */
data class InitialChangesUpdate<DM: IsRootModel>(
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
                dataModel = { DataObjectVersionedChange.Model }
            )
        )

        override fun invoke(values: SimpleObjectValues<InitialChangesUpdate<*>>) = InitialChangesUpdate<IsRootModel>(
            version = values(1u),
            changes = values(2u)
        )
    }
}
