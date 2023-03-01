package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions
import maryk.core.query.responses.statuses.StatusType.CHANGE_SUCCESS
import maryk.core.values.SimpleObjectValues

/** Change action was completed successfully with [version] */
data class ChangeSuccess<DM : IsRootModel>(
    val version: ULong,
    val changes: List<IsChange>?
) : IsChangeResponseStatus<DM> {
    override val statusType = CHANGE_SUCCESS

    @Suppress("unused")
    internal companion object : SimpleQueryModel<ChangeSuccess<*>>() {
        val version by number(1u, ChangeSuccess<*>::version, type = UInt64)
        val changes by list(
            index = 2u,
            getter = ChangeSuccess<*>::changes,
            required = false,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = ChangeType,
                definitionMap = mapOfChangeDefinitions
            ),
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: SimpleObjectValues<ChangeSuccess<*>>) = ChangeSuccess<IsRootModel>(
            version = values(1u),
            changes = values(2u)
        )
    }
}
