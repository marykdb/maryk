package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions
import maryk.core.query.responses.statuses.StatusType.ADD_SUCCESS
import maryk.core.values.SimpleObjectValues

/** Successful add of given object with [key], [version] and added [changes] */
data class AddSuccess<DM : IsRootDataModel<*>>(
    val key: Key<DM>,
    val version: ULong,
    val changes: List<IsChange>
) : IsAddResponseStatus<DM> {
    override val statusType = ADD_SUCCESS

    internal companion object : SimpleQueryDataModel<AddSuccess<*>>(
        properties = object : ObjectPropertyDefinitions<AddSuccess<*>>() {
            init {
                IsResponseStatus.addKey(this, AddSuccess<*>::key)
                add(2u, "version", NumberDefinition(type = UInt64), AddSuccess<*>::version)
                add(3u, "changes",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = MultiTypeDefinition(
                            typeEnum = ChangeType,
                            definitionMap = mapOfChangeDefinitions
                        )
                    ),
                    getter = maryk.core.query.responses.statuses.AddSuccess<*>::changes,
                    toSerializable = { TypedValue(it.changeType, it) },
                    fromSerializable = { it.value as IsChange }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AddSuccess<*>>) =
            AddSuccess<IsRootDataModel<IsPropertyDefinitions>>(
                key = values(1u),
                version = values(2u),
                changes = values(3u)
            )
    }
}
