package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.responses.updates.UpdateResponseType.InitialChanges
import maryk.core.values.SimpleObjectValues

/** Update containing the initial changes for listeners which listen to a scan changes. */
data class InitialChangesUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val version: ULong,
    val changes: List<DataObjectVersionedChange<DM>>
): IsUpdateResponse<DM, P> {
    override val type = InitialChanges

    @Suppress("unused")
    internal object Properties : ObjectPropertyDefinitions<InitialChangesUpdate<*, *>>() {
        val version by number(1u, getter = InitialChangesUpdate<*, *>::version, type = UInt64)
        val changes by list(
            index = 2u,
            getter = InitialChangesUpdate<*, *>::changes,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { DataObjectVersionedChange }
            )
        )
    }

    companion object : SimpleQueryDataModel<InitialChangesUpdate<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<InitialChangesUpdate<*, *>>) = InitialChangesUpdate<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            version = values(1u),
            changes = values(2u)
        )
    }
}
