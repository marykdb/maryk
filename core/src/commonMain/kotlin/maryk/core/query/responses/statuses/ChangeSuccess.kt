package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions
import maryk.core.query.responses.statuses.StatusType.CHANGE_SUCCESS
import maryk.core.values.SimpleObjectValues

/** Change action was completed successfully with [version] */
data class ChangeSuccess<DM : IsRootDataModel<*>>(
    val version: ULong,
    val changes: List<IsChange>?
) : IsChangeResponseStatus<DM> {
    override val statusType = CHANGE_SUCCESS

    internal companion object : SimpleQueryDataModel<ChangeSuccess<*>>(
        properties = object : ObjectPropertyDefinitions<ChangeSuccess<*>>() {
            init {
                add(1u, "version", NumberDefinition(type = UInt64), ChangeSuccess<*>::version)
                add(
                    2u, "changes",
                    ListDefinition(
                        required = false,
                        valueDefinition = InternalMultiTypeDefinition(
                            typeEnum = ChangeType,
                            definitionMap = mapOfChangeDefinitions
                        )
                    ),
                    getter = ChangeSuccess<*>::changes,
                    toSerializable = { TypedValue(it.changeType, it) },
                    fromSerializable = { it.value }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangeSuccess<*>>) = ChangeSuccess<IsRootDataModel<IsPropertyDefinitions>>(
            version = values(1u),
            changes = values(2u)
        )
    }
}
