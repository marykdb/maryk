package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions
import maryk.core.query.responses.statuses.StatusType.ADD_SUCCESS
import maryk.core.values.SimpleObjectValues

/** Successful add of given object with [key], [version] and added [changes] */
data class AddSuccess<DM : IsRootModel>(
    val key: Key<DM>,
    val version: ULong,
    val changes: List<IsChange>
) : IsAddResponseStatus<DM> {
    override val statusType = ADD_SUCCESS

    @Suppress("unused")
    internal companion object : SimpleQueryDataModel<AddSuccess<*>>(
        properties = object : ObjectPropertyDefinitions<AddSuccess<*>>() {
            val key by addKey(AddSuccess<*>::key)
            val version by number(2u, getter = AddSuccess<*>::version, type = UInt64)

            val changes by list(
                index = 3u,
                getter = AddSuccess<*>::changes,
                        default = emptyList(),
                        valueDefinition = InternalMultiTypeDefinition(
                            typeEnum = ChangeType,
                            definitionMap = mapOfChangeDefinitions
                    ),
                    toSerializable = { TypedValue(it.changeType, it) },
                    fromSerializable = { it.value }
                )
            }
    ) {
        override fun invoke(values: SimpleObjectValues<AddSuccess<*>>) =
            AddSuccess<IsRootModel>(
                key = values(1u),
                version = values(2u),
                changes = values(3u)
            )
    }
}
