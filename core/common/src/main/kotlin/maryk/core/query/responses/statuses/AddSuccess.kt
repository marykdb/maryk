package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
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

/** Successful add of given object with [key], [version] and added [changes] */
data class AddSuccess<DM: IsRootDataModel<*>>(
    val key: Key<*>,
    val version: UInt64,
    val changes: List<IsChange>
) : IsAddResponseStatus<DM> {
    override val statusType = StatusType.ADD_SUCCESS

    internal companion object: SimpleQueryDataModel<AddSuccess<*>>(
        properties = object : ObjectPropertyDefinitions<AddSuccess<*>>(){
            init {
                IsResponseStatus.addKey(this, AddSuccess<*>::key)
                add(1,"version", NumberDefinition(type = UInt64), AddSuccess<*>::version)
                add(2,"changes",
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
        override fun invoke(map: SimpleValues<AddSuccess<*>>) = AddSuccess<IsRootDataModel<IsPropertyDefinitions>>(
            key = map(0),
            version = map(1),
            changes = map(2)
        )
    }
}
